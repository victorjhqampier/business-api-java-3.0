# infrastructure/redis-infrastructure/AGENTS.md

Módulo de infraestructura Redis: Cliente Jedis 5.x optimizado para **alta demanda, operaciones atómicas y data-intensive caching**.

## Arquitectura del Módulo

```
com/arify/redisinfra/
├── RedisStarting.java                           # Factory - Inicialización de conexión Redis
└── general/
    └── RedisCacheInfrastructure.java           # Singleton @ApplicationScoped - Implementación de ICacheInfrastructure
```

## Principios de Alto Rendimiento (Data-Intensive Caching)

### 1. Priorización de Variables de Entorno

**Regla obligatoria**: Las configuraciones de conexión a Redis deben priorizarse en el siguiente orden:

1. **Variables de entorno (.env)** - PRIORIDAD MÁXIMA
2. **Valores por defecto (fallback)** - Solo si no existe la variable de entorno

**Patrón obligatorio en `GlobalStartUp.java`**:

```java
@Produces
@ApplicationScoped
public JedisPooled jedisPooled() {
    // Priorizar variables de entorno (.env) sobre application.properties
    String host = System.getenv("REDISDATABASE_HOST");
    String password = System.getenv("REDISDATABASE_PASSWD");
    String databaseStr = System.getenv("REDISDATABASE_DATABASE");
    String sslStr = System.getenv("REDISDATABASE_SSL");
    String abortStr = System.getenv("REDISDATABASE_ABORTONCONNECTFAIL");

    // Fallback a valores por defecto si no están en el environment
    if (host == null || host.isBlank()) {
        host = "localhost";
        LOGGER.warning("REDISDATABASE_HOST not found in environment, using default: localhost");
    }
    
    if (password == null || password.isBlank()) {
        password = "your-redis-password";
        LOGGER.warning("REDISDATABASE_PASSWD not found in environment, using default");
    }

    int database = 0;
    if (databaseStr != null && !databaseStr.isBlank()) {
        try {
            database = Integer.parseInt(databaseStr);
        } catch (NumberFormatException ex) {
            LOGGER.warning("REDISDATABASE_DATABASE invalid format, using default: 0");
        }
    }

    boolean ssl = false;
    if (sslStr != null && !sslStr.isBlank()) {
        ssl = Boolean.parseBoolean(sslStr);
    }

    boolean abortOnConnectFail = false;
    if (abortStr != null && !abortStr.isBlank()) {
        abortOnConnectFail = Boolean.parseBoolean(abortStr);
    }

    LOGGER.info("services.AddSingleton<JedisPooled>()");
    return RedisStarting.init(host, password, database, ssl, abortOnConnectFail);
}
```

**Beneficios**:
- **12-Factor App Compliance**: Configuración externa al código.
- **Docker/Kubernetes Ready**: Variables de entorno son el estándar en contenedores.
- **Seguridad**: Secrets no quedan hardcodeados en `application.properties`.
- **Logs Informativos**: Advierte cuando se usan valores por defecto.

### 2. Configuración con Archivo .env

**Recomendado para desarrollo local**:

Crear archivo `.env` en la raíz del proyecto:

```bash
REDISDATABASE_HOST=localhost
REDISDATABASE_PASSWD=my-secure-password
REDISDATABASE_DATABASE=0
REDISDATABASE_SSL=false
REDISDATABASE_ABORTONCONNECTFAIL=false
```

**Cargar variables antes de ejecutar**:

```bash
# Linux/Mac
export $(cat .env | xargs)
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev

# Windows PowerShell
Get-Content .env | ForEach-Object {
    $name, $value = $_.split('=')
    Set-Content env:\$name $value
}
```

**Docker Compose**:

```yaml
services:
  businessapi:
    env_file:
      - .env
    environment:
      - REDISDATABASE_HOST=redis
      - REDISDATABASE_PASSWD=${REDIS_PASSWORD}
```

### 3. Singleton de JedisPooled (Connection Pooling)

**Patrón obligatorio**: `JedisPooled` debe ser `@ApplicationScoped` (Singleton CDI) producido en `GlobalStartUp.java`.

```java
// GlobalStartUp.java (Technical Resources)
@Produces
@ApplicationScoped
public JedisPooled jedisPooled() {
    // ... leer variables de entorno
    return RedisStarting.init(host, password, database, ssl, abortOnConnectFail);
}
```

**Beneficios**:
- **Connection Pooling interno**: `JedisPooled` mantiene automáticamente un pool de conexiones TCP reutilizables.
- **Thread-Safe**: Múltiples hilos pueden usar la misma instancia sin problemas de concurrencia.
- **Keep-Alive**: Reutiliza conexiones TCP sin overhead de handshake repetido.
- **Zero Allocation**: No se crean instancias nuevas por request.

### 4. RedisStarting: Factory de Inicialización

**Responsabilidad**: Encapsular toda la lógica de configuración de Redis en una clase de infraestructura.

**Características (homologadas desde .NET)**:
- ✅ Connection Timeout: **500ms** (fail-fast)
- ✅ Socket Timeout: **500ms** (sync/async operations)
- ✅ Soporte SSL/TLS configurable
- ✅ Validación estricta de parámetros (host, password, database 0-15)
- ✅ AbortOnConnectFail: Control de comportamiento en fallo de conexión
- ✅ Ping inicial para verificar conectividad
- ✅ Manejo de errores con logs detallados

**Equivalente a .NET**:
```csharp
ConnectionMultiplexer.Connect(new ConfigurationOptions {
    EndPoints = { host },
    Password = password,
    DefaultDatabase = database,
    Ssl = ssl,
    AbortOnConnectFail = abortOnConnectFail,
    ConnectTimeout = 500,
    SyncTimeout = 500,
    AsyncTimeout = 500
});
```

### 5. ObjectMapper Estático (Zero Allocation)

**OBLIGATORIO**: `RedisCacheInfrastructure` tiene un `ObjectMapper` estático compartido:

```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
```

**Prohibido**: Crear `new ObjectMapper()` por cada operación de caché. Esto dispara classpath scanning y reflexión costosa.

### 6. Operaciones Atómicas con Lua Scripts

**OBLIGATORIO**: Las transiciones de estado críticas usan scripts Lua para garantizar atomicidad:

```java
private static final String UPDATE_WHEN_STATUS_MATCHES_SCRIPT = """
        local value = redis.call('GET', KEYS[1])
        if not value then
            return 0
        end
        
        local record = cjson.decode(value)
        if record.status ~= ARGV[1] then
            return 0
        end
        
        -- Validación de ownerToken para transición started → created
        if ARGV[1] == 'started' then
            local ownerToken = record.ownerToken
            if ownerToken ~= nil and ownerToken ~= cjson.null and ownerToken ~= '' and ownerToken ~= ARGV[4] then
                return 0
            end
        end
        
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
        return 1
        """;
```

**Beneficios**:
- **Atomicidad**: GET + validación + SET ocurren en una sola operación atómica.
- **Race Condition Prevention**: Múltiples threads/procesos no pueden sobrescribir datos de forma inconsistente.
- **Validación de ownerToken**: Previene que un proceso actualice un registro que otro proceso ya está procesando.

### 7. Circuit Breaker (1 segundo cooldown)

**OBLIGATORIO**: Protección contra colapso de la aplicación si Redis falla:

```java
private static final int UNAVAILABLE_COOLDOWN_MILLISECONDS = 1000;
private static final AtomicLong unavailableUntilUnixMilliseconds = new AtomicLong(0);

private void throwIfRedisTemporarilyUnavailable(String id, String operation) {
    long unavailableUntil = unavailableUntilUnixMilliseconds.get();
    if (Instant.now().toEpochMilli() < unavailableUntil) {
        LOGGER.log(Level.WARNING,
                "Redis cache {0} skipped because Redis is temporarily unavailable. CacheId=[{1}]",
                new Object[]{operation, id});
        throw new JedisConnectionException("Redis is temporarily unavailable");
    }
}

private static void markRedisTemporarilyUnavailable() {
    long unavailableUntil = Instant.now().toEpochMilli() + UNAVAILABLE_COOLDOWN_MILLISECONDS;
    unavailableUntilUnixMilliseconds.set(unavailableUntil);
}
```

**Beneficios**:
- **Fail-Fast**: Si Redis está caído, no intentamos reconectar en cada operación (evita saturar logs y CPU).
- **Cooldown Period**: Después de un error, esperamos 1 segundo antes de reintentar.
- **Application Resilience**: La aplicación sigue funcionando aunque Redis esté inaccesible (el caché simplemente se "salta").

### 8. Logger Estático

**OBLIGATORIO**: Todos los loggers en este módulo son `private static final`:

```java
private static final Logger LOGGER = Logger.getLogger(RedisCacheInfrastructure.class.getName());
```

Evita lookup de logger por cada request.

## Arquitectura de Wiring (Composition Root)

### Separación de Responsabilidades

**GlobalStartUp.java** (Technical Resources):
```java
// 1. Recurso técnico global: La conexión persistente
@Produces
@ApplicationScoped
public JedisPooled jedisPooled() {
    // Lee variables de entorno
    // Llama a RedisStarting.init(...)
    // Log: services.AddSingleton<JedisPooled>()
}
```

**InfrastructureStartUp.java** (Domain Adapters):
```java
// 2. Adaptador de dominio: Implementación de la interfaz de caché
@Produces
@ApplicationScoped
public ICacheInfrastructure cacheInfrastructure(JedisPooled jedisPooled) {
    LOGGER.info("services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()");
    return new RedisCacheInfrastructure(jedisPooled);
}
```

**Beneficios de esta Separación**:
1. **Reutilización**: Si creas otro microservicio interno que necesite Redis, inyectas el mismo `JedisPooled`.
2. **Mantenimiento**: Si cambias de Redis a Memcached, solo tocas el Adaptador; el Recurso Técnico se puede eliminar o cambiar sin afectar otros componentes.
3. **Performance**: Al ser Singletons, evitamos el "Handshake" de TCP en cada petición.

## Flujo de Ejecución (Data-Intensive Caching)

### Operaciones Asíncronas No Bloqueantes

```java
// 1. Crear registro en caché con TTL
CacheRecord<MyData> record = new CacheRecord<>(
    "user:123",
    CacheStatus.STARTED,
    myData,
    Instant.now().getEpochSecond(),
    Instant.now().plusSeconds(300).getEpochSecond(),
    "owner-token-xyz"
);

CompletableFuture<Boolean> createFuture = cacheInfrastructure
    .tryCreateAsync(record, Duration.ofMinutes(5), cancellationToken);

// 2. Obtener registro
CompletableFuture<CacheRecord<MyData>> getFuture = cacheInfrastructure
    .getAsync("user:123", cancellationToken);

// 3. Actualización condicional (transición atómica)
CompletableFuture<Boolean> updateFuture = cacheInfrastructure
    .tryUpdateAsync(
        updatedRecord,
        CacheStatus.STARTED,    // Estado esperado
        "owner-token-xyz",      // Token de propietario esperado
        Duration.ofMinutes(5),
        cancellationToken
    );
```

### Características del Flujo:

1. **Non-Blocking I/O**: Todas las operaciones retornan `CompletableFuture`.
2. **Virtual Thread Compatible**: Compatible con `@RunOnVirtualThread` en controllers.
3. **CancellationToken Integration**: Si el token se cancela, el `CompletableFuture` se aborta.
4. **Transiciones Atómicas**: `tryUpdateAsync` valida estado y ownerToken antes de actualizar.

## Transiciones de Estado Permitidas

```
STARTED → CREATED   (con validación de ownerToken)
CREATED → CLOSED    (sin validación de ownerToken)
```

**Prohibido**:
- STARTED → CLOSED (debe pasar por CREATED)
- CREATED → STARTED (no se puede revertir)
- CLOSED → cualquier otro estado (final)

## Prohibido

- Crear `new ObjectMapper()` fuera de `RedisCacheInfrastructure`.
- Instanciar `JedisPooled` manualmente (debe ser `@ApplicationScoped`).
- Usar `@ConfigProperty` para configuración de Redis (usar variables de entorno directamente con `System.getenv()`).
- Hardcodear secrets en `application.properties`.
- Logger no estático.
- **Usar `try-catch` en infrastructure o application**: Las excepciones deben fluir hacia `presentation`. Solo se permite `catch + throw` para enriquecer contexto antes de re-lanzar.
- Modificar estados sin usar `tryUpdateAsync` (bypass de validaciones atómicas).

## Variables de Entorno Requeridas

| Variable | Descripción | Valor por Defecto | Requerido |
|----------|-------------|-------------------|-----------|
| `REDISDATABASE_HOST` | Host de Redis | `localhost` | Sí* |
| `REDISDATABASE_PASSWD` | Contraseña de Redis | `your-redis-password` | Sí* |
| `REDISDATABASE_DATABASE` | Número de DB (0-15) | `0` | No |
| `REDISDATABASE_SSL` | Habilitar SSL/TLS | `false` | No |
| `REDISDATABASE_ABORTONCONNECTFAIL` | Abortar app si falla conexión | `false` | No |

\* Si no se configuran, se usan valores por defecto con logs de advertencia.

## Troubleshooting

### Problema: "REDISDATABASE_HOST not found in environment"

**Causa**: La variable de entorno no está configurada.

**Solución**:
```bash
# Linux/Mac
export REDISDATABASE_HOST=localhost
export REDISDATABASE_PASSWD=my-password

# Windows PowerShell
$env:REDISDATABASE_HOST="localhost"
$env:REDISDATABASE_PASSWD="my-password"

# Docker Compose
environment:
  - REDISDATABASE_HOST=redis
  - REDISDATABASE_PASSWD=secure-password
```

### Problema: "Failed to connect to Redis"

**Causa**: Redis no está corriendo o la configuración es incorrecta.

**Solución**:
```bash
# Verificar que Redis esté corriendo
docker ps | grep redis

# Iniciar Redis con Docker
docker run -d -p 6379:6379 --name redis redis:7-alpine

# Con contraseña
docker run -d -p 6379:6379 --name redis redis:7-alpine --requirepass my-password

# Probar conexión
redis-cli -h localhost -a my-password ping
```

### Problema: "Redis is temporarily unavailable"

**Causa**: Circuit breaker activado después de un error de conexión.

**Comportamiento esperado**: La aplicación espera 1 segundo antes de reintentar.

**Solución**: Verificar que Redis esté accesible y esperar el cooldown period.

## Dependencias Maven

```xml
<dependencies>
    <!-- Domain (interfaces) -->
    <dependency>
        <groupId>com.arify</groupId>
        <artifactId>domain</artifactId>
    </dependency>
    
    <!-- Redis Client: Jedis 5.x -->
    <dependency>
        <groupId>redis.clients</groupId>
        <artifactId>jedis</artifactId>
        <version>5.1.2</version>
    </dependency>
    
    <!-- Jackson para JSON serialization -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.0</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>2.17.0</version>
    </dependency>
</dependencies>
```

## Verificación de Configuración

```bash
# 1. Build completo
./presentation/businessapi/mvnw -f pom.xml clean install -DskipTests=true

# 2. Iniciar con variables de entorno
export REDISDATABASE_HOST=localhost
export REDISDATABASE_PASSWD=test-password
export REDISDATABASE_DATABASE=0

# 3. Ejecutar en dev mode
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev

# 4. Verificar logs de inicio
# Debe aparecer:
# INFO  [GlobalStartUp] services.AddSingleton<JedisPooled>()
# INFO  [RedisStarting] Initializing Redis connection: host=localhost, database=0, ssl=false
# INFO  [RedisStarting] Redis connection established successfully
# INFO  [InfrastructureStartUp] services.AddSingleton<ICacheInfrastructure, RedisCacheInfrastructure>()
```

## Comparativa: Java vs .NET

| Aspecto | .NET (StackExchange.Redis) | Java (Jedis 5.x + RedisStarting) | Estado |
|---------|----------------------------|----------------------------------|--------|
| **Timeout** | 500ms | 500ms | ✅ Idéntico |
| **SSL** | Configurable | Configurable | ✅ Idéntico |
| **Retry** | 1 | Circuit Breaker (1s) | ✅ Mejorado |
| **Validation** | Host/Pass/DB | Host/Pass/DB | ✅ Idéntico |
| **Abort** | AbortOnConnectFail | AbortOnConnectFail | ✅ Idéntico |
| **Config** | appsettings.json + Env | Variables de Entorno (.env) | ✅ Priorizado |
| **Logs** | AddSingleton<T> | AddSingleton<T> | ✅ Idéntico |
| **DI** | IServiceCollection | CDI @Produces | ✅ Equivalente |
| **Lifecycle** | IDisposable | @Disposes | ✅ Equivalente |
