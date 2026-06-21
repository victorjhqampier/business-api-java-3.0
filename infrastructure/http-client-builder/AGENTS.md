# infrastructure/http-client-builder/AGENTS.md

Módulo de infraestructura HTTP: Cliente nativo Java 21 optimizado para **alta demanda, múltiples servidores simultáneos y data-intensive I/O**.

## Arquitectura del Módulo

```
com/arify/httpclientbuilder/
├── HttpClientConnector.java       # Singleton @ApplicationScoped - Gestión del HttpClient
├── HttpClientBuilder.java         # Transient (per-request) - Fluent API para construir requests
├── InfrastructureLogger.java      # Utilidad de logging para infraestructura
└── entities/
    └── HttpResponseEntity.java    # Entidad de respuesta HTTP con JsonNode
```

## Principios de Alto Rendimiento (Data-Intensive I/O)

### 1. Singleton de HttpClient (Múltiples Servidores)

**Patrón obligatorio**: `HttpClientConnector` debe ser `@ApplicationScoped` (Singleton CDI).

```java
// Composition Root (AppConfiguration)
@Produces
@ApplicationScoped
public HttpClientConnector httpClientConnector() {
    return new HttpClientConnector(Duration.ofSeconds(5), Duration.ofSeconds(1));
}
```

**Beneficios**:
- **Connection Pooling interno**: Java `HttpClient` mantiene automáticamente un pool de conexiones para cada host destino.
- **Keep-Alive**: Reutiliza conexiones TCP sin overhead de handshake repetido.
- **Múltiples servidores simultáneos**: Un solo `HttpClient` puede gestionar conexiones a decenas de servidores diferentes sin interferencia.
- **HTTP/2 Multiplexing**: Con `HTTP_2` habilitado, múltiples requests al mismo host viajan por el mismo socket, reduciendo latencia y descriptores de archivo.

### 2. ObjectMapper Estático (Zero Allocation)

**OBLIGATORIO**: `HttpClientConnector` tiene un `ObjectMapper` estático compartido:

```java
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

public static ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
}
```

**Prohibido**: Crear `new ObjectMapper()` en builders, comandos o handlers. Esto dispara classpath scanning y reflexión costosa en cada instancia.

**Uso correcto**:
```java
// En cualquier clase de infrastructure
JsonNode content = HttpClientConnector.getObjectMapper().readTree(json);
String serialized = HttpClientConnector.getObjectMapper().writeValueAsString(data);
```

### 3. Parsing Centralizado con Logging de Errores

**OBLIGATORIO**: La deserialización JSON se centraliza en `HttpClientConnector.parseBodyToJson()`.

**Características**:
- **Parsing Único**: El body de la respuesta HTTP se parsea **una sola vez** en el Connector y se entrega como `JsonNode` en el `HttpResponseEntity`.
- **Logging Crítico**: Si ocurre un error de deserialización (`JsonProcessingException`), se registra un log de nivel **`SEVERE`** con contexto completo (URL, preview del body) antes de relanzar la excepción.
- **Propagación Natural**: La excepción se envuelve en `RuntimeException` para que fluya hacia la capa de presentación sin interrupciones.

```java
// HttpClientConnector.parseBodyToJson
public JsonNode parseBodyToJson(String body, String url) {
    if (body == null || body.isEmpty()) {
        return NullNode.getInstance();
    }
    
    try {
        return OBJECT_MAPPER.readTree(body);
    } catch (JsonProcessingException exception) {
        // LOG CRÍTICO con contexto
        LOGGER.log(Level.SEVERE, InfrastructureLogger.format(
            "SEVERE",
            "JSON deserialization failed",
            null,
            "{\"url\":\"" + url + "\",\"body_preview\":\"" + bodyPreview + "\"}",
            "\"" + exception.getMessage() + "\""));
        
        throw new RuntimeException("Failed to parse JSON response from: " + url, exception);
    }
}
```

**Beneficio**: 
- **Observabilidad**: Los errores de parsing se registran con contexto completo para diagnóstico en producción.
- **DRY**: El código de deserialización se escribe **una sola vez** en el Connector.
- **Zero Boilerplate**: Los comandos de infraestructura ya no necesitan manejar `JsonProcessingException`.

### 4. Logger Estático

**OBLIGATORIO**: Todos los loggers en este módulo son `private static final`:

```java
private static final Logger LOGGER = Logger.getLogger(HttpClientConnector.class.getName());
```

Evita lookup de logger por cada request.

### 5. HTTP/2 con Fallback Automático

El conector está configurado con `HttpClient.Version.HTTP_2`:

```java
.version(HttpClient.Version.HTTP_2)
```

**Beneficios**:
- **Multiplexing**: Múltiples requests simultáneos por una sola conexión TCP (si el servidor soporta HTTP/2).
- **Fallback transparente**: Si el servidor solo soporta HTTP/1.1, Java automáticamente degrada sin errores.
- **Header Compression**: HPACK reduce overhead en headers repetidos.

### 6. URL Building Optimizado (Hot Path)

El método `buildUrlWithParams` usa `StringBuilder` en lugar de `Streams`:

```java
// ✓ Correcto: StringBuilder (implementado)
StringBuilder queryString = new StringBuilder();
for (Map.Entry<String, String> entry : params.entrySet()) {
    queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
               .append("=")
               .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
}

// ✗ Evitar: Stream (antiguo)
String queryString = params.entrySet().stream()
    .map(...)
    .collect(Collectors.joining("&")); // Crea intermediarios
```

Reduce allocaciones en el hot path de construcción de URLs.

## Flujo de Ejecución (Data-Intensive)

### Request Asíncrono No Bloqueante

```java
// 1. Builder fluido (transient por request)
HttpClientBuilder builder = new HttpClientBuilder(connector, LOGGER);
builder.http("https://api.example.com")
       .endpoint("/users/{id}")
       .param("id", "123")
       .query("format", "json")
       .timeout(Duration.ofSeconds(5));

// 2. Disparo asíncrono (no bloquea el hilo)
CompletableFuture<HttpResponseEntity> future = builder.get(cancellationToken);

// 3. Composición con otros futures (parallel I/O)
CompletableFuture<HttpResponseEntity> future1 = builder1.get(token);
CompletableFuture<HttpResponseEntity> future2 = builder2.get(token);

CompletableFuture.allOf(future1, future2)
    .thenApplyAsync(ignored -> processResults(future1.join(), future2.join()), executor);
```

### Características del Flujo:

1. **Non-Blocking I/O**: `sendAsync` retorna inmediatamente un `CompletableFuture`.
2. **Virtual Thread Compatible**: El hilo que dispara el request no se bloquea; el I/O ocurre en hilos internos del HttpClient.
3. **Parallel Requests**: Múltiples requests a diferentes servidores se disparan simultáneamente sin esperar.
4. **CancellationToken Integration**: Si el token se cancela, el `CompletableFuture` se aborta vía `onCancel` callback.

## Gestión de Múltiples Servidores

### Connection Pooling por Host

Java `HttpClient` mantiene internamente un pool de conexiones **por cada host destino**:

```
HttpClient (Singleton)
├── Pool para api.server1.com
│   ├── Conexión 1 (Keep-Alive)
│   ├── Conexión 2 (Keep-Alive)
│   └── Conexión 3 (Keep-Alive)
├── Pool para api.server2.com
│   ├── Conexión 1 (Keep-Alive)
│   └── Conexión 2 (Keep-Alive)
└── Pool para api.server3.com
    └── Conexión 1 (Keep-Alive)
```

### Comportamiento Bajo Alta Demanda:

1. **Primer Request a Host Nuevo**: Establece conexión TCP + TLS handshake (~50-200ms).
2. **Requests Subsecuentes al Mismo Host**: Reutiliza conexión existente (~1-5ms de latency pura).
3. **Límite de Conexiones por Host**: Java limita automáticamente la cantidad de conexiones simultáneas al mismo host para evitar saturación. Si hay más requests que conexiones disponibles, se encolan y esperan.
4. **Timeout de Keep-Alive**: Las conexiones inactivas se cierran después del timeout del servidor (típicamente 60-120s), liberando recursos automáticamente.

### Ejemplo de Alta Concurrencia:

```java
// 100 requests simultáneos a 10 servidores diferentes (10 por servidor)
List<CompletableFuture<HttpResponseEntity>> futures = new ArrayList<>();

for (int i = 0; i < 100; i++) {
    String host = "https://api.server" + (i % 10) + ".com";
    HttpClientBuilder builder = new HttpClientBuilder(connector, LOGGER);
    builder.http(host)
           .endpoint("/data")
           .timeout(Duration.ofSeconds(5));
    
    futures.add(builder.get(cancellationToken));
}

// Espera a que todos completen sin bloquear platform threads
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenApplyAsync(ignored -> processAllResults(futures), executor);
```

**Resultado**: El mismo `HttpClient` (Singleton) gestiona las 100 conexiones en paralelo, reutilizando sockets y manteniendo Keep-Alive por cada host.

## CancellationToken Integration

### Flujo de Cancelación:

```java
CancellationToken token = CancellationToken.withTimeout(Duration.ofSeconds(9));

CompletableFuture<HttpResponseEntity> future = builder.get(token);

// Si el token se cancela (timeout o manual), el Future se cancela automáticamente
token.onCancel(() -> future.cancel(true));
```

**Beneficios**:
- **Liberación Temprana de Recursos**: Si el cliente HTTP cancela antes de recibir respuesta, la conexión se aborta inmediatamente.
- **Timeout Respeto**: El timeout más corto entre el del `HttpClient` y el del `CancellationToken` prevalece.
- **Propagación de Errores**: Si el token expira, se lanza `CancellationException` que fluye hacia el caso de uso y el controlador.

## Prohibido

- Crear `new ObjectMapper()` fuera de `HttpClientConnector`.
- Instanciar `HttpClientConnector` manualmente (debe ser `@ApplicationScoped`).
- Usar `HttpClientBuilder` como Singleton (debe ser transient por request).
- Forzar HTTP/1.1 sin justificación técnica (HTTP/2 es superior para data-intensive).
- Usar `Streams` para construcción de URLs o query params en hot path.
- Logger no estático.
- **Usar `try-catch` para control de flujo o manejo de errores esperados**: Las excepciones (`JsonProcessingException`, `IOException`, `TimeoutException`, errores de parsing) deben propagarse hacia `presentation`. Solo se permite `catch + throw` para enriquecer contexto antes de re-lanzar.

## Verificación de Rendimiento

### Comandos para medir latencia real:

```bash
# Build en modo jar
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am package -DskipTests

# Ejecutar
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar

# Medir con wrk (100 conexiones simultáneas, 10 threads, 30s)
wrk -t10 -c100 -d30s --latency http://localhost:8080/business-api-b/v1/no-bian/123/create

# Medir con hey (1000 requests, 50 concurrentes)
hey -n 1000 -c 50 -m POST -H "Content-Type: application/json" \
  -d '{"field":"value"}' \
  http://localhost:8080/business-api-b/v1/no-bian/123/create
```

### Métricas Esperadas (Post-Optimización):

- **Throughput**: 1000-5000 req/s (depende del hardware y red).
- **Latency p50**: < 5ms (sin I/O externo).
- **Latency p99**: < 20ms (sin I/O externo).
- **Connection Reuse**: > 95% (verificar con logs de Keep-Alive).

## Dependencias Maven

```xml
<dependencies>
    <!-- Java 21 nativo: java.net.http.HttpClient -->
    <!-- No requiere dependencias adicionales -->
    
    <!-- Jackson para JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

## Troubleshooting

### Problema: "Connection timeout" bajo alta carga

**Causa**: Límite de conexiones simultáneas del sistema operativo.

**Solución**:
```bash
# Linux/Mac: Aumentar límite de descriptores de archivo
ulimit -n 65536

# Verificar configuración
ulimit -n
```

### Problema: "Too many connections" del servidor destino

**Causa**: El servidor externo rechaza nuevas conexiones.

**Solución**:
- Implementar rate limiting en tu aplicación.
- Usar circuit breaker para proteger el servidor destino.
- Negociar con el proveedor del servicio para aumentar límites.

### Problema: Latencia alta en requests subsecuentes

**Causa**: Keep-Alive deshabilitado en el servidor destino.

**Solución**:
- Verificar que el servidor responda con `Connection: keep-alive`.
- Contactar al proveedor del servicio para habilitar Keep-Alive.
- Aumentar `connectTimeout` si la latencia de red es alta.
