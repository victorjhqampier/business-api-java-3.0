# Redis Infrastructure Module

Implementación **pura Java** del provider de cache usando Redis (Jedis 5.x) que implementa el contrato `ICacheInfrastructure` definido en el Cache Library Service.

## Características

- **Módulo Puro**: Sin dependencias de Quarkus/Jakarta. Solo Java 21 + Jedis + Jackson.
- **Operaciones atómicas** con Lua scripts para UPDATE condicional
- **Circuit breaker** de 1 segundo para evitar colapsar la aplicación si Redis falla
- **Serialización JSON** con Jackson (ObjectMapper estático para alto rendimiento)
- **Validación de ownerToken** en transiciones `started → created`
- **Timeout de 500ms** en conexión y socket
- **Fail-fast** en caso de fallo de Redis (circuit breaker)

## Arquitectura

```
infrastructure/redis-infrastructure/
├── pom.xml                          # Maven config (solo Jedis + Jackson)
├── README.md
└── com/arify/redisinfra/
    └── RedisCacheInfrastructure.java    # Implementación ICacheInfrastructure (407 líneas)
```

**Nota**: Este módulo NO contiene configuración CDI. El wiring se hace exclusivamente en `presentation/businessapi/src/main/java/com/arify/config/AppConfiguration.java` (Composition Root).

## Configuración

La configuración de Redis se maneja en el **Composition Root** (`AppConfiguration.java`) siguiendo el mandato de arquitectura del proyecto.

### Variables de Entorno (Producción)

```bash
export REDIS_HOST=redis.production.com
export REDIS_PASSWORD=your-secure-password
export REDIS_DATABASE=0
export REDIS_SSL=true
```

### application.properties (Desarrollo)

```properties
RedisDatabase.Host=localhost
RedisDatabase.Passwd=your-redis-password
RedisDatabase.Database=0
RedisDatabase.Ssl=false
```

**Nota**: En modo producción, las variables de entorno tienen prioridad sobre `application.properties`.

## Wiring en Composition Root

El módulo de Redis se conecta al resto de la aplicación en `presentation/businessapi/src/main/java/com/arify/config/AppConfiguration.java`:

```java
@ApplicationScoped
public class AppConfiguration {
    
    @ConfigProperty(name = "RedisDatabase.Host")
    Optional<String> redisHost;
    
    @ConfigProperty(name = "RedisDatabase.Passwd")
    Optional<String> redisPassword;
    
    // ... más campos de configuración
    
    /**
     * Produce el pool de conexiones Jedis como singleton.
     */
    @Produces
    @ApplicationScoped
    public JedisPooled jedisPooled() {
        // Lógica de construcción con configuración desde env/properties
        HostAndPort hostAndPort = new HostAndPort(host, 6379);
        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .password(password)
                .database(database)
                .connectionTimeoutMillis(500)
                .socketTimeoutMillis(500)
                .ssl(ssl)
                .build();
        return new JedisPooled(hostAndPort, config);
    }
    
    /**
     * Cierra el pool al apagar la aplicación.
     */
    public void closeJedisPool(@Disposes JedisPooled jedisPooled) {
        jedisPooled.close();
    }
    
    /**
     * Produce el provider de cache Redis como singleton.
     */
    @Produces
    @ApplicationScoped
    public ICacheInfrastructure cacheInfrastructure(JedisPooled jedisPooled) {
        return new RedisCacheInfrastructure(jedisPooled);
    }
}
```

## Uso en Casos de Uso

El `ICacheInfrastructure` se inyecta en los casos de uso a través del Composition Root:

```java
@ApplicationScoped
public class AppConfiguration {
    
    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(
            ICacheInfrastructure cacheInfrastructure,
            IFakeApiInfrastructure fakeApiInfrastructure,
            @Named("virtualThreadExecutor") ExecutorService executor) {
        
        // Crear servicio de cache con provider de Redis
        CacheLibraryService cache = new CacheLibraryService(cacheInfrastructure);
        
        return new ExampleUseCase(cache, fakeApiInfrastructure, executor);
    }
}
```

### Ejemplo en Caso de Uso

```java
public class ExampleUseCase implements ExamplePort {
    private final CacheLibraryService cache;
    private final IFakeApiInfrastructure fakeApiInfrastructure;
    private final ExecutorService executor;
    
    public ExampleUseCase(
            CacheLibraryService cache,
            IFakeApiInfrastructure fakeApiInfrastructure,
            ExecutorService executor) {
        this.cache = cache;
        this.fakeApiInfrastructure = fakeApiInfrastructure;
        this.executor = executor;
    }
    
    @Override
    public CompletableFuture<EasyResult<CreateExampleAdapter>> getDataAsync(
            TraceIdentifierAdapter trace,
            CreateExampleCommandAdapter command,
            CancellationToken cancellationToken) {
        
        String cacheKey = "example-" + command.customerId();
        
        return cache
            .forKey(cacheKey)
            .useStrategy(CacheStrategy.CACHE_THEN_SOURCE_AND_STORE)
            .withTtl(Duration.ofMinutes(10), Duration.ofMinutes(2))
            .resolveAsync(
                token -> fetchFromSource(trace, command, token),
                CreateExampleAdapter.class,
                cancellationToken
            )
            .thenApply(EasyResult::success);
    }
    
    private CompletableFuture<CreateExampleAdapter> fetchFromSource(
            TraceIdentifierAdapter trace,
            CreateExampleCommandAdapter command,
            CancellationToken token) {
        return fakeApiInfrastructure.createExampleAsync(trace, command, token);
    }
}
```

## Circuit Breaker

Cuando Redis falla (timeout, conexión perdida, etc.), el sistema activa un **circuit breaker** de **1 segundo**:

- Durante ese segundo, todas las llamadas a Redis fallan inmediatamente sin intentar conectarse.
- Después de 1 segundo, se intenta reconectar automáticamente.
- Esto evita que hilos se bloqueen esperando a un Redis caído.

## Lua Script para UPDATE Condicional

El método `tryUpdateAsync` usa un Lua script atómico que:

1. Verifica que el registro existe
2. Valida que el status actual coincide con el esperado
3. Si es transición `started → created`, valida el `ownerToken`
4. Solo si todo es válido, ejecuta el UPDATE

Esto garantiza **transiciones atómicas** y previene **race conditions** cuando múltiples instancias intentan actualizar el mismo registro.

## Dependencias

- **Jedis 5.1.2**: Cliente Redis optimizado para Java 21
- **Jackson 2.17.0**: Serialización JSON con soporte `JavaTimeModule`
- **Domain**: Contrato `ICacheInfrastructure`

**Nota**: Este módulo NO tiene dependencias de Quarkus. Es 100% portable y puede ser usado en cualquier framework (Spring, Micronaut, CLI, batch processing, etc.).

## Compilación

```bash
# Compilar solo este módulo
./presentation/businessapi/mvnw -f infrastructure/redis-infrastructure/pom.xml clean install

# Compilar proyecto completo
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true
```

## Testing Local con Docker

```bash
# Levantar Redis en Docker
docker run -d --name redis-cache \
  -p 6379:6379 \
  redis:7-alpine \
  redis-server --requirepass dev-password

# Configurar application.properties
RedisDatabase.Host=localhost
RedisDatabase.Passwd=dev-password
RedisDatabase.Database=0

# Ejecutar aplicación en dev mode
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev
```

## Performance

- **ObjectMapper estático**: Evita reinstanciar Jackson por request (ahorro ~100-500 µs/request).
- **Logger estático**: Elimina overhead de instanciación (ahorro ~10-50 µs/request).
- **Connection pooling**: Jedis reutiliza conexiones TCP (ahorro ~1-5 ms/request en cold start).
- **Timeout de 500ms**: Fail-fast para evitar bloquear hilos virtuales.
- **Circuit breaker**: Falla inmediatamente cuando Redis está caído (evita acumulación de requests).

## Troubleshooting

### Error: "Redis connection is not available"

**Causa**: Redis no está accesible en el host/puerto configurado.

**Solución**:
```bash
# Verificar que Redis está corriendo
redis-cli -h localhost -p 6379 -a your-password PING

# Revisar logs de conexión
tail -f presentation/businessapi/target/quarkus-app/quarkus.log | grep Redis
```

### Error: "Redis cache conditional transition skipped"

**Causa**: El status actual del registro no coincide con el esperado, o el `ownerToken` no coincide.

**Solución**: Este es un comportamiento esperado cuando múltiples instancias intentan actualizar el mismo registro. El sistema maneja esto automáticamente.

### Error: "Redis is temporarily unavailable"

**Causa**: Circuit breaker activado después de un fallo de Redis.

**Solución**: Esperar 1 segundo. El sistema intentará reconectar automáticamente. Si el problema persiste, verificar la disponibilidad de Redis.

## Arquitectura Limpia

Este módulo sigue estrictamente los principios de **Clean Architecture**:

1. **Independencia del Framework**: No contiene anotaciones de Quarkus/Jakarta. El wiring se hace en la capa de presentación.
2. **Testeable**: Puede ser probado unitariamente sin levantar el contenedor CDI.
3. **Portable**: Puede ser reutilizado en otros proyectos Java sin modificaciones.
4. **Alta Performance**: Optimizado según los mandatos de `AGENTS.md` (singletons, loggers estáticos, ObjectMapper estático).

## Referencias

- [Cache Library Service](../../domain/com/arify/domain/containers/cachelibraryservice/docs/COMPLETEDOC.md)
- [Jedis Documentation](https://github.com/redis/jedis)
- [Redis Commands](https://redis.io/commands/)
- [Composition Root Pattern](../../presentation/businessapi/AGENTS.md)
