# CacheLibraryService - Guia de uso rapido

## Setup

```csharp
using Domain.Containers.CacheLibraryService;
using Domain.Containers.CacheLibraryService.Internals;

var provider = new MemoryCacheInfrastructure();
var cache = new CacheLibraryService(provider);
```

En un microservicio real, el provider concreto (`ICacheInfrastructure`) debe venir por DI desde Infrastructure. `CacheLibraryService` no debe registrarse como singleton global: el caso de uso puede crear `new CacheLibraryService(provider)` con el provider inyectado. La instancia del builder es liviana; el recurso compartido y costoso vive en el provider, por ejemplo una conexion/pool Redis.

## Leer solo desde cache

```csharp
var result = await cache
    .ForKey("cfg-11-currencies")
    .UseStrategy(CacheStrategy.CacheOnly) // Read all time. Not Set status 
    .ResolveAsync<List<CurrencyAdapter>>(ct);
```

```csharp
var result = await cache
    .ForKey("cfg-11-currencies")
    .UseStrategy(CacheStrategy.CacheOnlyThenClose) // Read once and close cache data. Set status to closed.
    .ResolveAsync<List<CurrencyAdapter>>(ct);
```

## Cache con fallback al origen

```csharp
var result = await cache
    .ForKey($"trx-11-{productId}")
    .UseStrategy(CacheStrategy.CacheThenSource) // No data, no cache. Does not set status.
    .ResolveAsync(token => inventoryQuery.GetStockAsync(productId, token), ct);
```

## Cache con fallback y almacenamiento

```csharp
var result = await cache
    .ForKey($"cfg-11-{customerId}")
    .UseStrategy(CacheStrategy.CacheThenSourceAndStore) // No data, no cache. Stored data uses status created.
    .WithTtl(TimeSpan.FromMinutes(10), ttlJitter: TimeSpan.FromMinutes(2)) // Optional anti-stampede jitter. Must be >= 0 and < ttl / 3.
    .ResolveAsync(token => customerQuery.GetByIdAsync(customerId, token), ct);
```

## Forzar origen y almacenar

```csharp
var result = await cache
    .ForKey($"trx-11-{companyCode}")
    .UseStrategy(CacheStrategy.SourceAndStore) // No data, no cache. Stored data uses status created.
    .WithTtl(TimeSpan.FromMinutes(30), ttlJitter: TimeSpan.FromMinutes(2)) // Optional anti-stampede jitter. Must be >= 0 and < ttl / 3.
    .ResolveAsync(token => settingsQuery.GetCompanySettingsAsync(companyCode, token), ct);
```

## Almacenar directamente

```csharp
await cache
    .ForKey($"get-11-{messageId}")
    .UseStrategy(CacheStrategy.StoreOnly) // Store explicit data. Set status to created.
    .WithTtl(TimeSpan.FromMinutes(5), ttlJitter: TimeSpan.FromMinutes(1)) // Optional anti-stampede jitter. Must be >= 0 and < ttl / 3.
    .PutAsync(summary, ct); // return bool
```

```csharp
await cache
    .ForKey($"get-11-{messageId}")
    .UseStrategy(CacheStrategy.StoreOnlyOrReserve) // No data means reserve. Set status to started.
    .WithTtl(TimeSpan.FromMinutes(5), ttlJitter: TimeSpan.FromMinutes(1)) // Optional anti-stampede jitter. Must be >= 0 and < ttl / 3.
    .PutAsync(null, ct); // return bool
```

## Fisicamente en la base de datos

```json
{
  "id": "get-11-1700000000-001",
  "status": "started|created|closed",
  "cachedData": null,
  "createdAt": 1700000000,
  "expireIn": 1700000300
}
//or
{
  "id": "ms-riesgos-trx-customer-1346453",  
  "status": "created",
  "cachedData": {
    "idCli": "1346453",
    "dni": "47476453",
    "saldoDisponible": 15000.50,
    "movimientosUltimos30Dias": 45,
    "validadoEn": 1761153600
  },
  "createdAt": 1761153600,
  "expireIn": 1761157200
}
// or
{
  "id": "ms-riesgos-trx-customer-1346453",  
  "status": "created",
  "cachedData": [{
    "idCli": "1346453",
    "dni": "47476453",
    "saldoDisponible": 15000.50,
    "movimientosUltimos30Dias": 45,
    "validadoEn": 1761153600
  }],
  "createdAt": 1761153600,
  "expireIn": 1761157200
}

```

## Reglas

- `ForKey(...)` recibe una sola clave exacta y deterministica. Esa clave es el `id` fisico del registro.
- `CacheLibraryService` recibe el provider en su constructor. El provider puede venir por DI, pero `CacheLibraryService` no debe registrarse como singleton global.
- Si se necesita trabajar con dos providers, crear dos instancias de `CacheLibraryService`, cada una con su provider.
- La instancia de `CacheLibraryService` es liviana; la conexion, pool o cliente compartido pertenece a Infrastructure.
- El desarrollador compone la clave antes de llamar `ForKey(...)`; la libreria no recibe partes separadas de metadata.
- Los criterios de actualizacion son solo `id` y `status`.
- Solo los registros con `status = created` pueden leerse como cache hit funcional.
- Los registros con `status = started` o `status = closed` no pueden leerse como dato valido.
- Solo los registros con `status = started` pueden actualizarse para completar una reserva.
- Los registros con `status = created` o `status = closed` no pueden actualizar su data.
- `CacheOnlyThenClose` es la unica transicion permitida desde `created` hacia `closed`.
- Las estrategias con `Source` no crean `started`; si guardan data, crean directamente `status = created`.
- `started -> created` solo aplica cuando antes existio una reserva explicita con `StoreOnlyOrReserve`.
- Si una estrategia con `Source` encuentra un registro ya `created`, no debe intentar actualizarlo ni sobrescribirlo.
- Campos informativos como microservicio, canal o referencia no participan como criterios de actualizacion.
- `ttlJitter` es opcional y se usa para prevenir cache stampede variando aleatoriamente el vencimiento real.
- `ttlJitter` debe ser mayor o igual a cero y menor que un tercio del TTL.
- `ICacheInfrastructure` solo guarda, lee, actualiza, elimina y verifica por clave exacta.
- Las estrategias viven en `CacheLibraryService`, no en los providers.
- `CancellationToken` se propaga a loader y provider.
- Fallas o timeout del provider se tratan como cache miss o escritura omitida.
- Si el token del caso de uso se cancela, la cancelacion se propaga.
