# infrastructure/AGENTS.md

Principios de arquitectura de infraestructura para aplicaciones **Data-Intensive** de alto rendimiento con Java 21 Virtual Threads.

## Arquitectura General

La capa de infraestructura se divide en dos tipos de componentes:

### 1. Recursos Técnicos (Technical Resources)
Clientes y wrappers de bajo nivel que gestionan conexiones a servicios externos (Redis, HTTP, bases de datos, etc.). Estos se registran en `GlobalStartUp` como Singletons (`@ApplicationScoped`).

**Características**:
- Poseen un archivo `*Starting.java` con un método `init()` que encapsula la configuración.
- Leen variables de entorno priorizándolas sobre valores por defecto.
- Gestionan recursos compartidos (pools de conexiones, clients nativos).
- Se producen en `GlobalStartUp` del Composition Root.

**Ejemplos**:
- `JedisPooled` (vía `RedisStarting.init()`)
- `HttpClientConnector` (vía `HttpClientStarting.init(executor)`)

### 2. Adaptadores de Dominio (Domain Adapters)
Implementaciones de interfaces definidas en `domain/interfaces/`. Representan la lógica de negocio que interactúa con servicios externos. Estos se registran en `InfrastructureStartUp` como Singletons.

**Características**:
- **No poseen `*Starting.java`**. Se instancian directamente con `new` en el Composition Root.
- Reciben dependencias (recursos técnicos, executors) por constructor.
- Implementan interfaces de dominio puras (`IFakeApiInfrastructure`, `ICacheInfrastructure`).
- Mantienen el wiring visible y transparente en `InfrastructureStartUp`.

**Ejemplos**:
- `FakeApiCommand` (implementa `IFakeApiInfrastructure`)
- `RedisCacheInfrastructure` (implementa `ICacheInfrastructure`)

---

## Principios de Alto Rendimiento (Data-Intensive)

### 1. Virtual Threads Obligatorios para I/O-Bound

**Mandato**: Todas las implementaciones que realizan operaciones de I/O asíncronas deben recibir un `ExecutorService` (Virtual Threads) por constructor y usarlo en sus `CompletableFuture`.

**Prohibido**: Usar el pool por defecto de Java (`ForkJoinPool.commonPool`). Esto bloquea hilos de plataforma y limita la concurrencia.

**Patrón correcto**:
```java
// Infrastructure Adapter
public final class RedisCacheInfrastructure implements ICacheInfrastructure {
    private final JedisPooled jedis;
    private final ExecutorService executor;

    public RedisCacheInfrastructure(JedisPooled jedis, ExecutorService executor) {
        this.jedis = jedis;
        this.executor = executor;
    }

    @Override
    public <T> CompletableFuture<CacheRecord<T>> getAsync(String id, CancellationToken token) {
        return CompletableFuture.supplyAsync(() -> {
            // Operación bloqueante de Jedis
            String value = jedis.get(id);
            return deserialize(value);
        }, executor); // ✅ Virtual Threads
    }
}
```

**Wiring en `InfrastructureStartUp`**:
```java
@Produces
@ApplicationScoped
public ICacheInfrastructure cacheInfrastructure(
        JedisPooled jedisPooled, 
        @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
    LOGGER.info("services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()");
    return new RedisCacheInfrastructure(jedisPooled, virtualThreadExecutor);
}
```

### 2. Compatibilidad Total con Código Existente

**Regla de Oro**: Al agregar nuevas capacidades de alto rendimiento (como Virtual Threads), **siempre mantener constructores anteriores** para no romper código existente.

**Patrón de constructores sobrecargados**:
```java
public class HttpClientConnector {
    private final HttpClient client;
    private final Duration defaultTimeout;

    // Constructor original (mantiene compatibilidad)
    public HttpClientConnector(Duration defaultTimeout, Duration connectTimeout) {
        this.defaultTimeout = defaultTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // Constructor con Virtual Threads (Alto Rendimiento - Recomendado)
    public HttpClientConnector(Duration defaultTimeout, Duration connectTimeout, ExecutorService executor) {
        this.defaultTimeout = defaultTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor) // ✅ Inyección de Virtual Threads
                .build();
    }
}
```

### 3. Priorización de Variables de Entorno

**Regla**: Los `*Starting.java` deben leer configuraciones desde variables de entorno **antes** que desde valores por defecto.

**Patrón estándar**:
```java
public static HttpClientConnector init(ExecutorService executor) {
    String requestTimeoutStr = System.getenv("HTTP_CLIENT_REQUEST_TIMEOUT");
    String connectTimeoutStr = System.getenv("HTTP_CLIENT_CONNECT_TIMEOUT");

    // Fallback a valores por defecto si no están en el environment
    if (requestTimeoutStr == null || requestTimeoutStr.isBlank()) {
        requestTimeoutStr = "5";
        LOGGER.warning("HTTP_CLIENT_REQUEST_TIMEOUT not found in environment, using default: 5s");
    }

    Duration requestTimeout = Duration.ofSeconds(Long.parseLong(requestTimeoutStr));
    Duration connectTimeout = Duration.ofSeconds(Long.parseLong(connectTimeoutStr));

    return new HttpClientConnector(requestTimeout, connectTimeout, executor);
}
```

**Beneficios**:
- **12-Factor App Compliance**: Configuración externa al código.
- **Docker/Kubernetes Ready**: Variables de entorno son el estándar en contenedores.
- **Seguridad**: Secrets no quedan hardcodeados.

### 4. Singletons Estáticos para Componentes Pesados

**Mandato**: Todos los componentes pesados deben ser `private static final` o `@ApplicationScoped`.

**Ejemplos obligatorios**:
- `Logger`: `private static final Logger LOGGER = Logger.getLogger(...)`
- `ObjectMapper`: `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(...)`
- `Validators`: `private static final MyValidator VALIDATOR = new MyValidator()`

**Prohibido**: Crear instancias de estos componentes por request.

---

## Criterios para `*Starting.java`

### ✅ Cuándo crear un `*Starting.java`:

1. **El componente es un recurso técnico compartido** (client, pool, connector).
2. **Requiere configuración compleja** (timeouts, SSL, autenticación, variables de entorno).
3. **Encapsula detalles de bajo nivel** que no deben estar en el Composition Root.

**Ejemplos**:
- `RedisStarting.init()` → Crea `JedisPooled` (pool de conexiones Redis).
- `HttpClientStarting.init(executor)` → Crea `HttpClientConnector` (cliente HTTP nativo Java 21).

### ❌ Cuándo NO crear un `*Starting.java`:

1. **El componente es un adaptador de dominio** (implementa una interfaz de `domain`).
2. **La instanciación es trivial** (solo inyección de dependencias en el constructor).
3. **El wiring debe ser visible** en el Composition Root para claridad arquitectónica.

**Ejemplos**:
- `FakeApiCommand` → Se instancia con `new FakeApiCommand(queue, connector)` en `InfrastructureStartUp`.
- `RedisCacheInfrastructure` → Se instancia con `new RedisCacheInfrastructure(jedis, executor)` en `InfrastructureStartUp`.

---

## Flujo de Wiring (Composition Root)

### `GlobalStartUp.java` (Recursos Técnicos)
```java
@Produces
@ApplicationScoped
public ExecutorService virtualThreadExecutor() {
    LOGGER.info("services.AddSingleton<ExecutorService>(virtualThreadExecutor)");
    return Executors.newVirtualThreadPerTaskExecutor();
}

@Produces
@ApplicationScoped
public HttpClientConnector httpClientConnector(@Named("virtualThreadExecutor") ExecutorService executor) {
    LOGGER.info("services.AddSingleton<HttpClientConnector>()");
    return HttpClientStarting.init(executor);
}

@Produces
@ApplicationScoped
public JedisPooled jedisPooled() {
    LOGGER.info("services.AddSingleton<JedisPooled>()");
    return RedisStarting.init();
}
```

### `InfrastructureStartUp.java` (Adaptadores de Dominio)
```java
@Produces
@ApplicationScoped
public IFakeApiInfrastructure fakeApiInfrastructure(
        MicroserviceCallMemoryQueue memoryQueue, 
        HttpClientConnector httpClientConnector) {
    LOGGER.info("services.AddSingleton<IFakeApiInfrastructure, FakeApiCommand>()");
    return new FakeApiCommand(memoryQueue, httpClientConnector);
}

@Produces
@ApplicationScoped
public ICacheInfrastructure cacheInfrastructure(
        JedisPooled jedisPooled, 
        @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
    LOGGER.info("services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()");
    return new RedisCacheInfrastructure(jedisPooled, virtualThreadExecutor);
}
```

### `ApplicationStartUp.java` (Casos de Uso)
```java
@Produces
@ApplicationScoped
public CacheLibraryService cacheLibraryService(ICacheInfrastructure cacheInfrastructure) {
    LOGGER.info("services.AddSingleton<CacheLibraryService>()");
    return new CacheLibraryService(cacheInfrastructure);
}

@Produces
@ApplicationScoped
public ExamplePort exampleUseCase(
        IFakeApiInfrastructure fakeApiInfrastructure, 
        CacheLibraryService cacheLibraryService,
        @Named("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
    LOGGER.info("services.AddSingleton<ExamplePort, ExampleUseCase>()");
    return new ExampleUseCase(fakeApiInfrastructure, cacheLibraryService, virtualThreadExecutor);
}
```

---

## Beneficios de esta Arquitectura

### 🚀 Alto Rendimiento
- **Virtual Threads**: Escala de cientos a decenas de miles de conexiones simultáneas con el mismo hardware.
- **Zero Allocation**: Singletons reutilizables evitan asignaciones innecesarias.
- **Connection Pooling**: Recursos técnicos mantienen pools internos optimizados.

### 🧩 Mantenibilidad
- **Wiring Transparente**: El `InfrastructureStartUp` muestra claramente qué adaptadores se usan.
- **Separación de Responsabilidades**: Recursos técnicos vs. adaptadores de dominio.
- **Compatibilidad Total**: Constructores sobrecargados permiten migración gradual.

### 🔒 Seguridad y Configuración
- **Variables de Entorno Priorizadas**: Configuración externa y portable.
- **Logs Informativos**: Advertencias cuando se usan valores por defecto.
- **12-Factor App Compliance**: Listo para contenedores y orquestadores.

---

## Prohibiciones Arquitectónicas

1. **Usar `ForkJoinPool.commonPool`** en implementaciones que hacen I/O. Inyectar Virtual Threads obligatoriamente.
2. **Crear `ObjectMapper`, `Logger` o `Validators` no estáticos**. Deben ser `private static final`.
3. **Ocultar el wiring de adaptadores de dominio** en métodos `init()`. Mantener la transparencia en el Composition Root.
4. **Romper compatibilidad** de constructores existentes. Siempre agregar sobrecarga, no reemplazar.
5. **Hardcodear configuraciones sensibles**. Usar variables de entorno priorizadas.

---

## Verificación de Alto Rendimiento

```bash
# 1. Compilar proyecto
./presentation/businessapi/mvnw -f pom.xml clean compile -DskipTests=true

# 2. Verificar logs de inicio (debe mostrar Virtual Threads)
# services.AddSingleton<ExecutorService>(virtualThreadExecutor)
# services.AddSingleton<HttpClientConnector>()
# services.AddSingleton<JedisPooled>()
# services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()

# 3. Medir throughput (objetivo: >1000 req/s en I/O-bound)
wrk -t10 -c1000 -d30s --latency http://localhost:8080/business-api-b/v1/no-bian/123/create
```

---

## Estructura Final de Módulos

```
infrastructure/
├── http-client-builder/
│   ├── HttpClientConnector.java         (Recurso técnico)
│   ├── HttpClientStarting.java          (Factory - init(executor))
│   └── AGENTS.md
├── redis-infrastructure/
│   ├── RedisStarting.java               (Factory - init())
│   ├── RedisCacheInfrastructure.java    (Adaptador de dominio)
│   └── AGENTS.md
├── fake-api-infra/
│   ├── FakeApiStarting.java             (Config holder - NO init())
│   ├── FakeApiCommand.java              (Adaptador de dominio)
│   └── AGENTS.md
└── AGENTS.md                            (Este archivo - Principios generales)
```

---

**Última actualización**: Homologación completada con Virtual Threads inyectados en todos los adaptadores de I/O.
