# CacheLibraryService - Documento completo

## Objetivo

Este documento define el contrato unico de cache compartido para datos generados por backend y datos originados por canales externos.

La libreria cubre dos familias de uso:

- cache de backend para configuraciones, catalogos, consultas costosas y contexto transaccional;
- cache de canal para trazabilidad, control de consumo e idempotencia.

## Principio de arquitectura

El cerebro del cache es `CacheLibraryService` y su builder. Ahi viven las decisiones de estrategia, lectura funcional, reserva, cierre, TTL, `ttlJitter` y transiciones de estado.

`CacheLibraryService` recibe un provider en su constructor. El builder no recibe provider por operacion. El provider concreto (`ICacheInfrastructure`) puede venir por DI desde Infrastructure, pero `CacheLibraryService` no debe registrarse como singleton global en Application. La instancia de la libreria/builder es liviana y puede crearse dentro del caso de uso con `new CacheLibraryService(provider)`.

El recurso compartido y costoso vive en el provider: conexion Redis, pool, cliente DynamoDB, cliente DocumentDB o equivalente. Si un caso necesita trabajar con dos tecnologias o providers distintos, debe crear dos instancias de `CacheLibraryService`, cada una con su provider.

Las infraestructuras concretas como Redis o DynamoDB son storage tonto. Solo deben ejecutar operaciones basicas sobre registros ya modelados por la libreria:

- obtener por `id`;
- guardar por `id`;
- actualizar validando `id` y `status`;
- eliminar por `id`;
- verificar existencia por `id`.

La infraestructura no decide reglas funcionales, no interpreta negocio, no construye claves y no define estrategias. Solo recibe los parametros necesarios para consultar o mutar por `id` y `status`.

## Esquema unico

Todo registro cacheado usa el mismo esquema fisico:

```json
{
  "id": "canonical-cache-key",
  "status": "started|created|closed",
  "cachedData": null,
  "createdAt": 1700000000,
  "expireIn": 1700000300
}
```

### Campos

| Campo | Tipo | Regla |
|---|---|---|
| `id` | string | Obligatorio. Es la clave fisica y canonica del registro. |
| `status` | string | Obligatorio. Valores permitidos: `started`, `created`, `closed`. |
| `cachedData` | object, array o null | Contenido funcional cacheado. No define por si solo el ciclo de vida. |
| `createdAt` | number | Epoch seconds UTC de creacion. |
| `expireIn` | number | Epoch seconds UTC de expiracion absoluta. |

Campos como microservicio, canal, referencia, metodo o tabla pueden formar parte del `id`, pero no se reciben separados como criterios de actualizacion.

## Clave canonica

`ForKey(...)` recibe una sola clave exacta y deterministica. Esa clave es el `id` fisico del registro.

La libreria no construye ni interpreta partes de la clave. El desarrollador debe componerla antes de llamar `ForKey(...)`.

Ejemplos para backend:

```txt
cfg-11-currencies
cfg-11-customer-47476453
trx-11-stock-PRD-001
ms-riesgos-trx-customer-1346453
```

Ejemplos para canal e idempotencia:

```txt
get-11-MSG-001
post-11-MSG-001
update-11-MSG-001
```

Para idempotencia, el `id` debe componerse al menos con operacion, canal y `messageId` externo:

```txt
{operation}-{channelId}-{messageId}
```

## Estados

`status` controla el ciclo de vida del registro.

| Status | Significado | Lectura funcional | Actualizacion |
|---|---|---:|---:|
| `started` | Registro reservado o proceso en curso. | No | Si |
| `created` | Registro completo, valido y listo para lectura. | Si | Solo cierre |
| `closed` | Registro cerrado, consumido o invalidado. | No | No |

Reglas obligatorias:

- solo `status = created` puede leerse como cache hit funcional;
- `status = started` y `status = closed` no son datos validos para lectura;
- solo `status = started` puede actualizarse para completar una reserva;
- `status = created` no puede actualizar su data, solo puede cerrarse con una transicion controlada a `closed`;
- `status = closed` no puede actualizarse;
- `cachedData = null` no significa automaticamente "no cachear"; el significado lo define `status`.

## Estrategias

| Estrategia | Lee cache | Ejecuta origen | Guarda | Uso principal |
|---|---:|---:|---:|---|
| `CacheOnly` | Si | No | No | Leer dato ya cacheado o validar existencia. |
| `CacheOnlyThenClose` | Si | No | Cierra | Leer una vez y pasar a `closed`. |
| `CacheThenSource` | Si | Si, si no existe | No | Usar cache si existe sin poblar en miss. |
| `CacheThenSourceAndStore` | Si | Si, si no existe | Si | Cache backend reusable. |
| `SourceAndStore` | No | Si | Si | Refresh o recalentamiento forzado. |
| `StoreOnly` | No | No | Si | Guardar dato completo como `created`. |
| `StoreOnlyOrReserve` | No | No | Si | Crear reserva `started` cuando no hay dato. |

Nota: estas estrategias viven en `CacheLibraryService`; los providers solo ejecutan operaciones basicas por `id` y `status`.

Las estrategias con `Source` (`CacheThenSource`, `CacheThenSourceAndStore`, `SourceAndStore`) no inician registros en `started`. Cuando guardan un resultado valido, el registro se crea directamente con `status = created`.

La transicion `started -> created` solo aplica cuando antes hubo una reserva explicita con `StoreOnlyOrReserve`. Si una estrategia con `Source` encuentra un registro ya `created`, no debe intentar actualizarlo ni sobrescribirlo.

## Backend cache

Usar backend cache cuando el microservicio construye, valida y administra el dato cacheado.

Casos tipicos:

- configuraciones;
- catalogos;
- consultas costosas;
- datos transaccionales reutilizables;
- contexto temporal generado por el propio backend.

Tipos sugeridos para componer el `id`:

```txt
cfg
trx
```

Ejemplo:

```json
{
  "id": "cfg-11-currencies",
  "status": "created",
  "cachedData": [
    {
      "currencyCode": "PEN",
      "enabled": true
    }
  ],
  "createdAt": 1761150000,
  "expireIn": 1761236400
}
```

Reglas:

- `cfg` se usa para configuracion, catalogos y datos de baja variacion;
- `trx` se usa para contexto transaccional o datos de volatilidad controlada;
- si el origen devuelve `null` en estrategias automaticas, no se debe crear cache;
- `CacheThenSourceAndStore` y `SourceAndStore` crean registros directamente como `created`;
- las estrategias de backend cache no usan `started` salvo que el flujo haya creado una reserva explicita;
- si un dato backend deja de ser valido, debe cerrarse, eliminarse o expirar segun el flujo.

## Channel cache e idempotencia

Usar channel cache cuando la informacion llega desde un canal, evento o mensaje externo.

Casos tipicos:

- trazabilidad de mensajes;
- validacion posterior;
- consumo controlado;
- idempotencia para `post` y `update`;
- cierre luego de consumo.

Tipos sugeridos para componer el `id`:

```txt
get
post
update
```

Ejemplo:

```json
{
  "id": "post-11-MSG-001",
  "status": "created",
  "cachedData": {
    "paymentId": "PAY-783423",
    "state": "ACCEPTED",
    "processedAt": 1761198605
  },
  "createdAt": 1761198605,
  "expireIn": 1761199805
}
```

Flujo de idempotencia recomendado:

1. Componer `id = {operation}-{channelId}-{messageId}`.
2. Leer con `CacheOnly`.
3. Si no existe, crear reserva con `StoreOnlyOrReserve` y `status = started`.
4. Ejecutar el caso de uso real.
5. Completar la reserva pasando a `status = created`.
6. Si llega otro request con el mismo `id`:
   - `started`: responder en proceso o aplicar espera controlada;
   - `created`: tratar como duplicado idempotente o devolver resultado previo;
   - `closed`: no reprocesar.

Para idempotencia estricta, la creacion de `started` debe ser atomica. Dos procesos no deben poder reservar el mismo `id` al mismo tiempo.

## No data y reservas

La ausencia de payload no debe confundirse con ausencia de cache.

| Caso | Status | `cachedData` |
|---|---|---|
| No cachear | No existe registro | No aplica |
| Reserva en curso | `started` | `null` |
| Resultado sin payload | `created` | `null` |
| Dato listo | `created` | object o array |
| Cerrado o consumido | `closed` | opcional |

En estrategias automaticas como `CacheThenSourceAndStore` y `SourceAndStore`, si el origen devuelve `null`, no se debe crear registro.

En `StoreOnlyOrReserve`, `PutAsync(null, ct)` representa una reserva y debe crear `status = started`.

`started` no es un estado normal de las estrategias con `Source`. Es un estado de reserva explicita. Por eso, una escritura automatica de `CacheThenSourceAndStore` o `SourceAndStore` debe crear `created` directamente; solo debe completar `started -> created` si la key ya estaba reservada.

## Ejemplos de uso por estrategia

### Idempotencia POST

Objetivo: evitar que una operacion de creacion se ejecute mas de una vez para el mismo mensaje.

`id` sugerido:

```txt
post-{channelId}-{messageId}
```

Estrategias:

1. `CacheOnly`: consultar si ya existe el `id`.
2. `StoreOnlyOrReserve`: si no existe, crear reserva `started`.
3. `StoreOnly`: si el proceso termina correctamente, guardar resultado `created`.

Lectura del estado:

- `started`: otro proceso ya lo esta ejecutando;
- `created`: operacion ya procesada, tratar como duplicado idempotente;
- `closed`: no reprocesar.

### Cachear una llamada a un metodo de infrestrutura

Objetivo: evitar llamar repetidamente a un origen costoso cuando el resultado puede reutilizarse.

`id` sugerido:

```txt
trx-11-customer-{customerId}
```

Estrategia:

1. `CacheThenSourceAndStore`: leer cache; si no existe, ejecutar el metodo origen y guardar el resultado como `created`.

Reglas:

- si el metodo origen devuelve `null`, no se crea cache;
- usar `ttlJitter` cuando varias claves puedan expirar juntas;
- usar `SourceAndStore` cuando se requiera refrescar el dato ignorando el cache actual.

### Recuperar calculos de una API previa

Objetivo: una API posterior necesita leer datos o calculos que una API anterior dejo cacheados.

`id` sugerido:

```txt
get-11-calculation-{messageId}
```

Estrategias:

1. API previa usa `StoreOnly` para guardar el calculo como `created`.
2. API posterior usa `CacheOnly` para recuperar el calculo.
3. Si el dato debe consumirse una sola vez, usar `CacheOnlyThenClose`.

Reglas:

- si el registro esta `created`, puede usarse como cache hit funcional;
- si esta `started`, la API previa aun no termino el calculo;
- si esta `closed`, el calculo ya fue consumido o invalidado;
- si no existe, la API posterior debe responder ausencia controlada o recalcular segun el caso de uso.

## Cache stampede prevention

Para evitar cache stampede, las escrituras pueden aplicar `ttlJitter`.

`ttlJitter` varia aleatoriamente el vencimiento real del registro para evitar que muchas claves expiren al mismo tiempo.

Reglas:

- `ttlJitter` es opcional;
- debe ser mayor o igual a cero;
- debe ser menor que un tercio del TTL;
- no reemplaza la reserva atomica para claves muy calientes.

Ejemplo:

```csharp
.WithTtl(TimeSpan.FromMinutes(10), ttlJitter: TimeSpan.FromMinutes(2))
```

Para claves muy consultadas, combinar `ttlJitter` con reserva atomica:

```txt
missing/expired -> started -> created
```

## Redis

Implementacion recomendada:

```txt
KEY: {id}
VALUE: JSON serializado del registro completo
TTL: expireIn - createdAt
```

Operacion base:

```txt
SET {id} {jsonString} EX {ttlSeconds}
```

Para reservas atomicas:

```txt
SET {id} {jsonString} NX EX {ttlSeconds}
```

Reglas:

- Redis actua como storage tonto; no contiene reglas de negocio ni estrategias;
- `ttlSeconds > 0`;
- `expireIn > createdAt`;
- lectura funcional solo cuando `status = created`;
- actualizacion de data solo cuando el registro actual esta en `started`, para completar una reserva explicita;
- una escritura automatica no debe actualizar registros ya `created`;
- cierre controlado solo desde `created` hacia `closed`.

## DynamoDB

Estructura recomendada:

| Atributo | Tipo | Rol |
|---|---|---|
| `id` | String | Partition Key |
| `status` | String | Dato |
| `cachedData` | String o Document | Dato |
| `createdAt` | Number | Dato |
| `expireIn` | Number | TTL |

Lectura:

- DynamoDB actua como storage tonto; no contiene reglas de negocio ni estrategias;
- `GetItem` por `id`;
- retornar `null` si no existe, expiro, esta en `started` o esta en `closed`;
- retornar dato solo si `status = created`.

Reservas y actualizaciones:

- crear `started` con condicion `attribute_not_exists(id)`;
- completar reserva con condicion `id = :id AND status = :started`;
- no actualizar data en registros `created` o `closed`;
- crear resultados de estrategias con `Source` directamente como `created`;
- cerrar con condicion `id = :id AND status = :created`.

## Responsabilidades

El consumidor de la libreria es responsable de:

- componer un `id` deterministico;
- elegir la estrategia correcta;
- definir TTL y `ttlJitter`;
- no guardar datos sensibles sin proteccion;
- decidir si un dato se cierra, elimina o expira;
- documentar la estructura funcional de `cachedData` en el punto de integracion.

El provider de cache es responsable de:

- comportarse como storage tonto;
- persistir por clave exacta;
- aplicar TTL real;
- aplicar `ttlJitter` cuando corresponda;
- ejecutar CRUD basico por `id`;
- ejecutar actualizaciones condicionales por `id` y `status`;
- distinguir actualizacion de data y cierre controlado;
- implementar reservas atomicas cuando el motor lo permita.

## Limites

- Tamano maximo recomendado por registro: 512 KB para mensajes de canal y 1 MB para cache backend.
- Si el contenido excede el limite, dividirlo en registros mas pequenos.
- La libreria no garantiza orden ni exactly-once por si sola.
- La idempotencia depende de un `id` canonico, estado correcto y operaciones atomicas del provider.
