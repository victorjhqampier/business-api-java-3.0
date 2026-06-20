# Plan de Homologacion: Python FastAPI → Java/Quarkus

Homologacion de `business-api-3.0` (Python/FastAPI) hacia `business-api-java-3.0` (Java 21 + Quarkus 3.34.1).

## Arquitectura destino

```
┌─ presentation/businessapi (JAX-RS controllers) ─┐
├─ application (use cases, ports, adapters) ───────┤
├─ domain (entities, domain services, interfaces) ─┤
├─ infrastructure/http-client-builder ─────────────┤
├─ infrastructure/fake-api-infra ──────────────────┤
├─ infrastructure/redis-infrastructure (NUEVO) ────┤
└──────────────────────────────────────────────────┘
```

## Decisiones tecnicas

| Aspecto | Eleccion | Justificacion |
|---------|----------|---------------|
| Cliente HTTP | `java.net.http.HttpClient` | Nativo Java 11+, zero dependencias, compatible virtual threads |
| Cliente Redis | `lettuce-core` | Cliente standalone; evita dependencias Quarkus fuera de presentation |
| Memoria eventos | `LinkedBlockingQueue` + virtual thread | Simple, alineado con virtual threads existentes |
| Paralelismo | `StructuredTaskScope` (JEP 428) | Java 21 structured concurrency con deadline |
| Timeouts | `StructuredTaskScope.ShutdownOnTimeout` | Nativo Java 21, sin dependencias extra |
| Background svc | Lifecycle Quarkus solo en presentation + virtual thread | El framework queda en presentation; la logica queda en Java puro |
| Configuracion | `application.properties` + composition root en presentation | Estandar Quarkus sin contaminar application/domain/infrastructure |

## Regla Clean Architecture

Quarkus/Jakarta/CDI solo puede existir en `presentation/businessapi`. Las capas `domain`, `application` e `infrastructure/*` deben ser Java puro: sin `@ApplicationScoped`, `@Inject`, `@Singleton`, `@Startup`, `@Observes`, `@ConfigProperty`, ni dependencias `io.quarkus:*`.

El wiring se hace con un **Composition Root** en presentation:

```
presentation/businessapi/src/main/java/com/arify/config/
  AppConfiguration.java  → @Produces para construir use cases e infrastructure adapters
```

Patron esperado:

```java
// application: Java puro
public class ExampleRedisUsecase {
    public ExampleRedisUsecase(IFakeApiInfrastructure fakeApi, ICacheInfrastructure cache) { ... }
}

// infrastructure: Java puro
public class CachedDataStore implements ICacheInfrastructure {
    public CachedDataStore(StatefulRedisConnection<String, String> connection) { ... }
}

// presentation: unico lugar con Quarkus/CDI
@ApplicationScoped
public class AppConfiguration {
    @Produces
    ExampleRedisUsecase exampleRedisUsecase(IFakeApiInfrastructure fakeApi, ICacheInfrastructure cache) {
        return new ExampleRedisUsecase(fakeApi, cache);
    }
}
```

## Alcance de pruebas

Durante las fases de migracion funcional **no se contemplan tests unitarios**. La validacion de cada fase se hara con compilacion, ejecucion local y pruebas manuales/funcionales del endpoint o comportamiento entregado. Los tests unitarios, de integracion y de regresion se agregaran al final en una fase dedicada de homologacion de pruebas.

---

## Fase 1: Cliente HTTP real + FakeApi

### Contexto
Python usa `httpx.AsyncClient` con `HttpClientBuilder` fluido. Java tiene `HttpClientBuilder` como placeholder vacio.

### Que crear

```
infrastructure/http-client-builder/com/arify/application/
  HttpClientBuilder.java     → builder fluido real (reemplazar placeholder)
```

`HttpClientBuilder` API:

```java
// Uso esperado
HttpClientBuilder
    .http("https://jsonplaceholder.typicode.com")
    .endpoint("/users/%d", id)
    .timeout(9)
    .getAsync(HttpResponse.BodyHandlers.ofString());  // CompletableFuture<HttpResponse<String>>
```

```
infrastructure/fake-api-infra/com/arify/application/
  FakeApiCommand.java          → implementa IFakeApiInfrastructure
  FakeApiStarting.java         → enum con URLs base
```

```
application/com/arify/application/ports/
  IFakeApiInfrastructure.java  → interfaz con getTitleAsync, getUserAsync
```

```
domain/com/arify/domain/entities/
  FakeApiEntity.java           → record { userId, id, title, completed }
```

### Validacion funcional

```
FakeApiCommand command = new FakeApiCommand(httpClientBuilder);
FakeApiEntity user = command.getUserAsync(1).get(5, SECONDS);
assert user.id() == 1;
```

---

## Fase 2: Caso de uso basico (sin cache)

### Contexto
Python `ExampleUsecase` valida trace + request, llama FakeApi en paralelo (`asyncio.gather`), retorna `EasyResult<CreateExampleAdapter>`.

### Que crear

```
application/com/arify/application/adapters/
  CreateExampleAdapter.java      → record { name, age, email }
  ExampleRequestAdaper.java      → record { channelId, messageId, deviceId }
```

```java
// ExampleRequestAdaper con validator embebido
public record ExampleRequestAdaper(
    String channelIdentification,
    String messageIdentification,
    String deviceIdentifier
) {
    public List<ValidationResultAdapter> validate() {
        var errors = new ArrayList<ValidationResultAdapter>();
        validateField("channelIdentification", channelIdentification, errors);
        validateField("messageIdentification", messageIdentification, errors);
        validateField("deviceIdentifier", deviceIdentifier, errors);
        return List.copyOf(errors);
    }
}
```

```
application/com/arify/application/usecases/exampleusecase/
  ExampleUsecase.java  → reemplazar: valida → fork paralelo → mapea resultado
```

Flujo:

```
1. FluentValidationExecutor.validate(traceIdentifier)
2. request.validate()
3. Si errores → EasyResult.failure("VALIDATION_FAILED", errores)
4. StructuredTaskScope:
     fork -> fakeApi.getUserAsync(request.channelIdentification())
     fork -> fakeApi.getTitleAsync(request.messageIdentification())
5. join() con deadline 9s
6. Si algun null → EasyResult.empty()
7. EasyResult.success(new CreateExampleAdapter(name, age, email))
```

```
presentation/businessapi/src/main/java/com/arify/controllers/
  ExampleController.java  → POST /api/v1/example
```

### Validacion funcional

```
POST /api/v1/example
{"channelIdentification": "CH12345", "messageIdentification": "MSG12345", ...}
→ 200 {"name": "...", "age": ..., "email": "..."}

POST /api/v1/example
{"channelIdentification": "", ...}
→ 422 [{code: "21002", message: "Field is required", field: "channelIdentification"}]
```

---

## Fase 3: Redis + CacheLibraryService

### Contexto
Python tiene `CacheLibraryService` con 8 estrategias, builder fluido, owner-based locking, TTL jitter. `CachedDataStore` implementa `ICacheInfrastructure` via Redis.

### Que crear

Nuevo modulo Maven:

```
infrastructure/redis-infrastructure/
  pom.xml                              → lettuce-core dependency (sin Quarkus)
  com/arify/application/
    RedisEngineSetting.java            → Java puro, crea RedisClient desde config object
    CachedDataStore.java               → Java puro, implementa ICacheInfrastructure
```

Wiring Quarkus en presentation:

```
presentation/businessapi/src/main/java/com/arify/config/
  RedisConfiguration.java              → @Produces connection/client y cierra recursos
  ApplicationCompositionRoot.java      → @Produces use cases y ports
```

Dominio (nuevos paquetes):

```
domain/com/arify/domain/containers/cachelibraryservice/
  ICacheInfrastructure.java            → interfaz (get, tryCreate, tryUpdate, remove, exists)
  CacheStatus.java                     → enum { STARTED, CREATED, CLOSED }
  CacheStrategy.java                   → enum { CACHE_ONLY, CACHE_THEN_SOURCE_AND_STORE, ... }
  CacheRecord.java                     → record { id, status, cachedData, createdAt, expireIn, ownerToken }
  CacheBuilder.java                    → builder fluido
  CacheLibraryService.java             → entrypoint estatico
```

Registro en parent `pom.xml`:

```xml
<module>infrastructure/redis-infrastructure</module>
```

`CachedDataStore` contract:

```java
// Operaciones con 500ms timeout
// Circuit breaker: 1s cooldown tras fallo
// JSON serialization con Jackson
// Lua script para tryUpdateAsync (validacion atomica status + owner)
@Override
public CompletableFuture<Optional<String>> getAsync(String key);                          // GET key
@Override
public CompletableFuture<Boolean> tryCreateAsync(String key, String value, Duration ttl); // SET NX PX
@Override
public CompletableFuture<Boolean> tryUpdateAsync(String key, String value,
    String expectedStatus, String ownerToken);                                            // Lua EVAL
@Override
public CompletableFuture<Void> removeAsync(String key);                                   // DEL
@Override
public CompletableFuture<Boolean> existsAsync(String key);                                // EXISTS
```

### Validacion funcional

```java
// Cache miss
var result = CacheLibraryService
    .forKey("test-key")
    .useStrategy(CACHE_THEN_SOURCE_AND_STORE)
    .withTTL(Duration.ofMinutes(5))
    .resolveAsync(() -> CompletableFuture.completedFuture("loaded"));

assertEquals("loaded", result.get());

// Cache hit (segunda llamada)
var cached = CacheLibraryService
    .forKey("test-key")
    .useStrategy(CACHE_THEN_SOURCE_AND_STORE)
    .resolveAsync(() -> { throw new RuntimeException("no debe llamarse"); });

assertEquals("loaded", cached.get());
```

---

## Fase 4: Caso de uso con cache Redis

### Contexto
Python `ExampleRedisUsecase` = `ExampleUsecase` + cache Redis con estrategia `CACHE_THEN_SOURCE_AND_STORE`.

### Que crear

```
application/com/arify/application/usecases/exampleusecase/
  ExampleRedisUsecase.java
```

Flujo:

```
1. Validar trace + request (igual Fase 2)
2. Para cada llamada FakeApi:
     key = "business-trx-" + prefix + "-" + id
     CacheLibraryService.forKey(key)
         .useStrategy(CACHE_THEN_SOURCE_AND_STORE)
         .withTTL(5, MINUTES)
         .resolveAsync(() -> fakeApi.getUserAsync(id))
3. Si algun resultado empty → EasyResult.empty()
4. EasyResult.success(CreateExampleAdapter)
```

Controller:

```
presentation/businessapi/.../controllers/
  ExampleController.java  → + GET /api/v1/example/cached?id={id}
```

### Validacion funcional

```
GET /api/v1/example/cached?id=1
→ 200 (primera vez: cache miss, llama FakeApi, popula cache, retorna)
GET /api/v1/example/cached?id=1
→ 200 (segunda vez: cache hit, NO llama FakeApi, retorna datos cacheados)
```

---

## Fase 5: Idempotencia (reservation pattern)

### Contexto
Python `ExampleIdempotencyUsecase` con patron de reserva atomica en Redis: GET idempotente y POST idempotente.

### Que crear

```
application/com/arify/application/adapters/
  ExamplePreRequestAdaper.java       → record + validator
  PostValidationIdemAdapter.java     → record { channelId, deviceId, response }
```

```
application/com/arify/application/usecases/exampleusecase/
  ExampleIdempotencyUsecase.java
```

**GET idempotente**:

```
1. Validar trace
2. key = "idem-get-" + traceId
3. CacheLibraryService.forKey(key).useStrategy(STORE_ONLY_OR_RESERVE)
     .withTTL(1, MINUTE).withJitter(10, SECONDS)
     .resolveAsync(() -> ejecutar_fake_api())
4. Si no se pudo reservar → 409 Conflict
5. Ejecutar FakeApi
6. CacheLibraryService.forKey(key).useStrategy(STORE_ONLY)
     .withOwner(token).withTTL(5, MINUTES+30s jitter)
     .resolveAsync(() -> resultado)
7. EasyResult.success(resultado)
```

**POST idempotente**:

```
1. Validar trace + request
2. key = "idem-post-" + traceId
3. Si existe en cache → retornar resultado cachead
4. Reservar atomicamente con STORE_ONLY_OR_RESERVE
5. Si no reservado → poll por resultado concurrente o 409
6. Recuperar preview via CACHE_ONLY_THEN_CLOSE
7. Validar channel/device match contra preview
8. Completar reserva con 5min TTL
9. EasyResult.success(resultado)
```

Controller:

```
ExampleController.java
  POST /api/v1/example/idempotent   → body + Idempotency-Key header
  GET  /api/v1/example/idempotent   → Idempotency-Key header
```

### Validacion funcional

```
POST /api/v1/example/idempotent
Idempotency-Key: uuid-1
→ 200 (primera vez)

POST /api/v1/example/idempotent
Idempotency-Key: uuid-1
→ 200 (misma respuesta, NO ejecuta logica de negocio)

POST /api/v1/example/idempotent
Idempotency-Key: uuid-2
→ 409 (request concurrente, no pudo reservar)
```

---

## Fase 6: Memory Events + Background Service

### Contexto
Python tiene `MicroserviceCallMemoryQueue` (basado en `asyncio.Queue`/C# `Channel<T>`) con listener background que consume trazas.

### Que crear

```
domain/com/arify/domain/containers/memoryevents/
  MicroserviceCallMemoryQueue.java   → LinkedBlockingQueue wrapper
  MicroserviceCallTrace.java          → record { identity, traceId, channelId, ... }
```

`MicroserviceCallMemoryQueue` API:

```java
public class MicroserviceCallMemoryQueue {
    private final LinkedBlockingQueue<MicroserviceCallTrace> queue;

    public MicroserviceCallMemoryQueue(int capacity)  // default 1500
    public boolean push(MicroserviceCallTrace trace)         // non-blocking, returns false if full
    public boolean offer(MicroserviceCallTrace trace, long timeout, TimeUnit unit) // blocking with timeout
    public MicroserviceCallTrace poll(long timeout, TimeUnit unit)  // blocking take with timeout
    public List<MicroserviceCallTrace> drainAll()           // drain all available
    public int size()
}
```

```
presentation/businessapi/src/main/java/com/arify/background/
  MemoryTraceListener.java     → @Startup bean con virtual thread
  MicroserviceTraceMiddleware.java  → captura request/respuesta
```

`MemoryTraceListener`:

```java
@ApplicationScoped
public class MemoryTraceListener {
    void onStart(@Observes StartupEvent ev) {
        Thread.ofVirtual().name("memory-listener").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                var batch = queue.drainAll();
                for (var trace : batch) {
                    log.info("Trace: {}", trace);
                    // TODO: llamar use case
                }
                Thread.sleep(1000); // backpressure
            }
        });
    }
}
```

### Validacion funcional

```java
var queue = new MicroserviceCallMemoryQueue(100);
queue.push(new MicroserviceCallTrace("trace-1", ...));
var item = queue.poll(1, SECONDS);
assertNotNull(item);

// Listener consume en background
var listener = new MemoryTraceListener(queue);
queue.push(new MicroserviceCallTrace("trace-2", ...));
// Log muestra "Trace: MicroserviceCallTrace[...]"
```

---

## Fases: orden y dependencias

```
Fase 1 ─── HTTP + FakeApi
  └── Fase 2 ─── Basic Use Case (depende de Fase 1)
  └── Fase 3 ─── Redis + CacheLibrary (independiente)
        └── Fase 4 ─── Cached Use Case (depende Fase 2 + 3)
        └── Fase 5 ─── Idempotency (depende Fase 2 + 3)
Fase 6 ─── Memory Events (independiente)
```

Cada fase debe compilar y dejar evidencia funcional antes de avanzar a la siguiente. Los tests automatizados se implementan en la fase final.

---

## Forma de trabajo

```
Fase N:
  1. Crear/Modificar archivos Java
  2. Compilar: ./presentation/businessapi/mvnw -f pom.xml compile -pl <modulo> -am
  3. Ejecutar aplicacion si la fase expone endpoint o background service
  4. Validar manualmente el comportamiento definido en "Validacion funcional"
  5. Verificar compilacion completa: ./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true
```

---

## Fase 7: Homologacion de pruebas automatizadas

### Contexto

Esta fase se ejecuta despues de completar la migracion funcional. Aqui se portan y agregan las pruebas unitarias, de integracion y de regresion necesarias para cerrar la homologacion.

### Que crear

Pruebas unitarias para componentes puros:

| Componente | Cobertura esperada |
|------------|-------------------|
| `EasyResult` | success/failure/empty e inmutabilidad |
| `FluentValidationExecutor` | required/min/max length |
| `CacheLibraryService` | estrategias, fail-open/fail-required, owner locking, TTL |
| `MicroserviceCallMemoryQueue` | push, poll, drain, backpressure, complete |
| Use cases | validacion, success, empty, failure, timeouts |

Pruebas de integracion en `presentation/businessapi/src/test/`:

| Componente | Cobertura esperada |
|------------|-------------------|
| Controllers | status codes, payloads, headers |
| Redis adapter | Redis real/Testcontainers o provider equivalente |
| Background listener | consume eventos en memoria |
| Health endpoints | SmallRye Health live/ready/health |

### Validacion final

```bash
./presentation/businessapi/mvnw -f pom.xml test
./presentation/businessapi/mvnw -f pom.xml verify -DskipITs=false
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true
```

La homologacion termina cuando todos los tests automatizados pasan y el build completo queda verde.

---

## Modulos nuevos vs existentes

| Modulo | Estado | Accion |
|--------|--------|--------|
| `domain` | existe | Agregar nuevos paquetes |
| `application` | existe | Agregar use cases, adapters, ports |
| `infrastructure/http-client-builder` | existe (placeholder) | Implementar HttpClientBuilder real |
| `infrastructure/fake-api-infra` | existe (placeholder) | Implementar FakeApiCommand |
| `infrastructure/redis-infrastructure` | **NUEVO** | Crear modulo completo |
| `presentation/businessapi` | existe | Agregar controllers, background services |

### parent pom.xml: agregar modulo

```xml
<module>infrastructure/redis-infrastructure</module>
```

### infrastructure/redis-infrastructure/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <parent>
        <groupId>com.arify</groupId>
        <artifactId>businessapi-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>redis-infrastructure</artifactId>
    <packaging>jar</packaging>
    <build>
        <sourceDirectory>${project.basedir}</sourceDirectory>
    </build>
    <dependencies>
        <dependency>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
            <version>6.5.5.RELEASE</version>
        </dependency>
    </dependencies>
</project>
```
