# CacheLibraryService - Guia tecnica para providers

## Objetivo

Este documento describe como implementar un provider para `CacheLibraryService`.

Aplica a Redis, DynamoDB, DocumentDB, MongoDB, SQL, memoria u otro storage. El provider debe ser storage tonto: no decide estrategias, no interpreta negocio y no construye claves. La inteligencia vive en `CacheLibraryService` y su builder.

## Contrato

Todo provider implementa:

```csharp
public interface ICacheInfrastructure
{
    Task<CacheRecord<T>?> GetAsync<T>(string id, CancellationToken cancellationToken = default);
    Task<bool> TryCreateAsync<T>(CacheRecord<T> record, TimeSpan ttl, CancellationToken cancellationToken = default);
    Task<bool> TryUpdateAsync<T>(CacheRecord<T> record, CacheStatus expectedStatus, TimeSpan ttl, CancellationToken cancellationToken = default);
    Task<bool> RemoveAsync(string id, CancellationToken cancellationToken = default);
    Task<bool> ExistsAsync(string id, CancellationToken cancellationToken = default);
}
```

Todas las firmas deben mantener los argumentos en una sola linea.

## Modelo fisico

El storage debe persistir el registro completo:

```json
{
  "id": "canonical-cache-key",
  "status": "started|created|closed",
  "cachedData": null,
  "createdAt": 1700000000,
  "expireIn": 1700000300
}
```

Reglas:

- `id` es la clave fisica unica.
- `status` debe serializarse como `started`, `created` o `closed`.
- `cachedData` puede ser `null`, object o array.
- `createdAt` y `expireIn` son epoch seconds UTC.
- El TTL real del storage debe estar alineado con `expireIn - now`.

## Responsabilidad del provider

El provider solo debe:

- obtener un registro por `id`;
- crear un registro por `id` solo si no existe;
- actualizar un registro por `id` solo si el `status` actual coincide con `expectedStatus`;
- eliminar por `id`;
- verificar existencia por `id`;
- aplicar TTL;
- propagar `CancellationToken`;
- registrar logs tecnicos cuando el flujo no sea feliz.

El provider no debe:

- decidir estrategias;
- ejecutar loaders/source;
- componer keys;
- interpretar negocio;
- reintentar indefinidamente;
- bloquear el caso de uso cuando el storage esta caido;
- exponer detalles del proveedor hacia Domain, Application o Presentation.

## Operaciones obligatorias

### GetAsync

Debe consultar por `id`.

Comportamiento:

- si no existe, retornar `null`;
- si expiro, eliminar cuando sea barato/seguro y retornar `null`;
- si existe, retornar `CacheRecord<T>`;
- si `cachedData` no puede deserializarse a `T`, retornar `null` y registrar `Warning`;
- si ocurre falla tecnica, registrar `Error` y lanzar la excepcion para que el builder aplique fail-open cuando corresponda.

No debe filtrar por estrategia. Puede devolver `started`, `created` o `closed`; el builder decide si es hit funcional.

### TryCreateAsync

Debe crear el registro solo si `id` no existe.

Comportamiento:

- si crea, retornar `true`;
- si ya existe, retornar `false`;
- no sobrescribir registros existentes;
- aplicar TTL recibido;
- registrar `Warning` si no crea por conflicto esperado;
- registrar `Error` si ocurre falla tecnica.

Esta operacion soporta dos casos:

- estrategias con `Source`: crear directamente `created`;
- reservas explicitas: crear `started` desde `StoreOnlyOrReserve`.

### TryUpdateAsync

Debe actualizar de forma condicional por `id` y `expectedStatus`.

Transiciones permitidas:

| Estado actual esperado | Nuevo estado | Uso |
|---|---|---|
| `started` | `created` | Completar reserva explicita. |
| `created` | `closed` | Cierre controlado. |

Reglas:

- si el estado actual no coincide con `expectedStatus`, retornar `false`;
- si la transicion no esta permitida, retornar `false`;
- no actualizar data de registros `created`;
- no actualizar registros `closed`;
- registrar `Warning` para transicion no permitida o condicion no cumplida;
- registrar `Error` para fallas tecnicas.

Importante: las estrategias con `Source` no deben pasar por `started` como flujo normal. Crean `created` directamente con `TryCreateAsync`.

### RemoveAsync

Debe eliminar por `id`.

Comportamiento:

- retornar `true` si elimino;
- retornar `false` si no existia;
- registrar `Warning` si no existia y eso sale del flujo feliz del provider;
- registrar `Error` ante falla tecnica.

### ExistsAsync

Debe verificar existencia por `id`.

Comportamiento:

- retornar `true` si el storage reporta existencia;
- retornar `false` si no existe;
- registrar `Error` ante falla tecnica.

## Fail-open

El builder trata fallas del provider como no bloqueantes para estrategias con `Source`:

- `CacheThenSource`;
- `CacheThenSourceAndStore`;
- `SourceAndStore`.

Para que esto funcione:

- el provider debe propagar excepciones tecnicas;
- el provider no debe hacer retries largos;
- el provider debe respetar `CancellationToken`;
- los timeouts deben ser cortos;
- si el storage esta caido, debe fallar rapido.

El builder captura la falla tecnica y ejecuta el loader/source cuando la estrategia lo permite.

## Logging obligatorio

En Infrastructure, toda operacion fuera del flujo feliz debe loguear.

Usar `Warning` para:

- conflicto esperado de create;
- condicion de update no cumplida;
- transicion no permitida;
- registro expirado;
- registro corrupto;
- payload no deserializable;
- remove de registro inexistente cuando sea relevante.

Usar `Error` para:

- desconexion del proveedor;
- timeout tecnico;
- excepcion del SDK;
- error de serializacion inesperado;
- error de permisos o autenticacion contra el storage.

No loguear:

- `cachedData`;
- secrets;
- connection strings;
- tokens;
- datos personales innecesarios.

## TTL

El builder calcula `expireIn` y envia `ttl`.

El provider debe:

- rechazar `ttl <= 0`;
- usar TTL nativo del motor cuando exista;
- preservar `expireIn`;
- eliminar o ignorar registros expirados al leer;
- evitar TTL infinito salvo decision explicita.

`ttlJitter` ya llega aplicado desde el builder. El provider no debe recalcularlo.

## Serializacion

Recomendaciones:

- serializar `status` en camelCase: `started`, `created`, `closed`;
- usar un formato estable y versionable;
- evitar serializar tipos concretos de Application o Presentation en metadatos del provider;
- deserializar `cachedData` de forma generica hacia `T`;
- ante payload incompatible con `T`, retornar `null` y loguear `Warning`.

## Redis

Implementacion recomendada:

```txt
GET {id}
SET {id} {json} NX PX {ttlMilliseconds}
EVAL update-if-status-matches
DEL {id}
EXISTS {id}
```

Create:

```txt
SET key payload NX PX ttl
```

Update condicional:

```lua
local value = redis.call('GET', KEYS[1])
if not value then return 0 end
local record = cjson.decode(value)
if record.status ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
return 1
```

Configuracion recomendada:

- `IConnectionMultiplexer` singleton;
- `AbortOnConnectFail` configurable;
- `BacklogPolicy.FailFast`;
- `ConnectRetry` bajo;
- `ConnectTimeout`, `SyncTimeout`, `AsyncTimeout` cortos;
- `AllowAdmin = false`;
- no crear conexiones por request.

## DynamoDB

Tabla recomendada:

| Atributo | Tipo | Rol |
|---|---|---|
| `id` | String | Partition Key |
| `status` | String | Dato |
| `cachedData` | Map/String | Dato |
| `createdAt` | Number | Dato |
| `expireIn` | Number | TTL |

Create:

```txt
PutItem
ConditionExpression: attribute_not_exists(id)
```

Update condicional:

```txt
UpdateItem
Key: id
ConditionExpression: #status = :expectedStatus
```

Lectura:

```txt
GetItem por id
```

Notas:

- no usar Scan para resolver cache;
- TTL de DynamoDB puede eliminar con retraso, por eso el provider debe validar `expireIn` al leer;
- usar expresiones condicionales para atomicidad.

## DocumentDB o MongoDB

Coleccion recomendada:

```json
{
  "_id": "canonical-cache-key",
  "status": "created",
  "cachedData": {},
  "createdAt": 1700000000,
  "expireIn": 1700000300
}
```

Indices:

- `_id` unico;
- TTL index sobre fecha de expiracion si el motor lo soporta.

Create:

```txt
insertOne
```

Si duplica `_id`, retornar `false`.

Update condicional:

```txt
updateOne({ _id: id, status: expectedStatus }, { $set: record })
```

Si `ModifiedCount = 0`, retornar `false`.

Notas:

- no usar queries por payload;
- no usar full collection scan;
- validar expiracion al leer porque los TTL indexes pueden tener retraso.

## SQL

Tabla minima:

```txt
id varchar(...) primary key
status varchar(...)
cached_data json/text/blob
created_at bigint
expire_in bigint
```

Create:

```sql
INSERT ... WHERE NOT EXISTS
```

o manejar conflicto de primary key y retornar `false`.

Update condicional:

```sql
UPDATE cache_table
SET status = @newStatus, cached_data = @data, created_at = @createdAt, expire_in = @expireIn
WHERE id = @id AND status = @expectedStatus
```

Si filas afectadas = 0, retornar `false`.

## Checklist del provider

- Implementa `ICacheInfrastructure`.
- Usa `id` como unica clave.
- `TryCreateAsync` no sobrescribe.
- `TryUpdateAsync` valida `id + expectedStatus`.
- Solo permite `started -> created` y `created -> closed`.
- Propaga `CancellationToken`.
- Falla rapido si el proveedor esta caido.
- Loguea `Warning` y `Error` segun corresponda.
- No loguea payload ni secretos.
- Aplica TTL real.
- No contiene reglas de negocio ni estrategias.
