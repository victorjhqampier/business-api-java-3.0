# HttpClientBuilder - Documentación

## Resumen

`HttpClientBuilder` es un cliente HTTP fluent para Java 21 que envuelve `java.net.http.HttpClient` (JDK nativo). Incluye un sistema opcional de captura de eventos en memoria para observabilidad, que solo se activa cuando es necesario.

## Arquitectura

```
HttpClientBuilder (fluent API, transient)
    └── HttpClientConnector (singleton, connection pool)
            └── java.net.http.HttpClient (JDK built-in)
```

- **`HttpClientConnector`**: Singleton que maneja el pool de conexiones, timeouts y keep-alive. Se produce en `AppConfiguration` con `@Produces @ApplicationScoped`.
- **`HttpClientBuilder`**: Instancia por-uso (transient). Se crea con `new HttpClientBuilder(connector, logger)`.

## Uso Básico

### 1. Inyección del Connector (CDI)

```java
@ApplicationScoped
public class MyService {
    private final HttpClientConnector connector;
    private final MicroserviceCallMemoryQueue memoryQueue;

    public MyService(HttpClientConnector connector, MicroserviceCallMemoryQueue memoryQueue) {
        this.connector = connector;
        this.memoryQueue = memoryQueue;
    }
}
```

### 2. GET Simple

```java
Logger logger = Logger.getLogger(MyService.class.getName());

HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://jsonplaceholder.typicode.com")
    .endpoint("todos/1")
    .get();

System.out.println("Status: " + response.statusCode());
System.out.println("Body: " + response.body());
```

### 3. GET con Query Params

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("search")
    .query("filter", "active")
    .query("sort", "desc")
    .get();
```

### 4. GET con Path Params

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("users/{id}/profile")
    .param("id", "12345")
    .get();
// URL final: https://api.example.com/users/12345/profile
```

### 5. Headers Personalizados

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("profile")
    .header("X-Client-Version", "1.0.0")
    .header("X-Device", "Desktop")
    .get();
```

### 6. Múltiples Headers (batch)

```java
Map<String, String> customHeaders = Map.of(
    "X-Request-ID", "abc123",
    "X-Custom-Token", "xyz789"
);

HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("batchHeaders")
    .headers(customHeaders)
    .get();
```

### 7. Autenticación Bearer

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("reports")
    .authorization("Bearer", "MiTokenSecreto123")
    .get();
// Header: Authorization: Bearer MiTokenSecreto123
```

### 8. POST con Body

```java
Map<String, Object> payload = Map.of(
    "title", "Nuevo Item",
    "description", "Creando un item via API"
);

HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("items")
    .post(payload);
```

### 9. PUT con Body

```java
Map<String, Object> updateData = Map.of(
    "status", "active",
    "updatedBy", "admin"
);

HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("items/{id}")
    .param("id", "999")
    .put(updateData);
```

### 10. Timeout Personalizado

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.slow-service.com")
    .endpoint("heavy-operation")
    .timeout(Duration.ofSeconds(30))
    .get();
```

## Captura de Eventos (Memory Queue)

### Activar Trazabilidad

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("users")
    .withMemoryQueue(memoryQueue, "User.Create.execute", "user_ops")
    .post(userData);
```

Parámetros de `withMemoryQueue()`:
- `queue`: Instancia de `MicroserviceCallMemoryQueue` (inyectada por CDI)
- `operationName`: Nombre de la operación (ej: `"Transfer.GetBalance.execute"`)
- `keyword`: Tag opcional para filtrar eventos

### Sin Captura (Máximo Rendimiento)

```java
HttpResponseEntity response = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com")
    .endpoint("health")
    .get();  // Sin withMemoryQueue() = sin overhead
```

### Reutilización con Reset

```java
HttpClientBuilder builder = new HttpClientBuilder(connector, logger)
    .http("https://api.example.com");

// Operación con memory queue
builder.endpoint("users")
    .withMemoryQueue(memoryQueue, "User.List", null)
    .get();

// Reset para nueva operación sin memory
builder.resetMemoryState();

// Operación sin memory (más rápida)
builder.endpoint("health").get();
```

## Optimizaciones Clave

### 1. **Activación Condicional**
- La captura de eventos solo se ejecuta si se llama `withMemoryQueue()`
- Flag `memoryEnabled` evita verificaciones innecesarias en cada request
- Cero overhead cuando no se usa memory queue

### 2. **Serialización Optimizada**
- Método `serializePayload()` con manejo eficiente de tipos
- Usa Jackson con configuración compacta
- Truncado automático a 4KB para payloads grandes (`...[truncated]`)

### 3. **Uso de tryPush()**
- Evita bloqueos usando `tryPush()` en lugar de `pushAsync()`
- No bloquea el flujo principal si la cola está llena
- Mejor rendimiento en escenarios de alta concurrencia

### 4. **Gestión de Memoria Eficiente**
- `identity` único por cada trace (UUID)
- Campos opcionales con valores por defecto
- Método `resetMemoryState()` para limpiar estado entre operaciones

### 5. **Timestamps Optimizados**
- `startDatetime` se captura una sola vez al crear el builder
- `responseDatetime` se genera solo al capturar la traza
- Reduce llamadas a `OffsetDateTime.now()`

## HttpResponseEntity

```java
public record HttpResponseEntity(
    int statusCode,           // Código HTTP (200, 404, 500, etc.)
    String body,              // Cuerpo de respuesta (raw)
    Map<String, List<String>> headers,  // Headers de respuesta
    String url                // URL final (después de redirects)
) {}
```

## Beneficios de Rendimiento

1. **Cero Overhead**: Sin memory queue, no hay impacto en rendimiento
2. **No Bloqueos**: `tryPush()` nunca bloquea el hilo principal
3. **Connection Pool**: Un solo `HttpClient` para toda la app (singleton)
4. **Virtual Threads Ready**: API síncrona que escala con virtual threads
5. **Escalabilidad**: Funciona bien bajo alta concurrencia

## Consideraciones

- La cola tiene capacidad limitada (1500 por defecto, configurable en `AppConfiguration`)
- Si la cola está llena, los eventos se descartan con warning en log
- El `resetMemoryState()` es opcional pero recomendado para reutilización del builder
- El builder NO es thread-safe (crear uno por operación o por thread)

## Manejo de Errores

### Errores HTTP (4xx/5xx)
```java
HttpResponseEntity response = builder.get();

if (response.statusCode() >= 400) {
    // El request se completó pero con error HTTP
    System.err.println("Error: " + response.statusCode());
    System.err.println("Body: " + response.body());
}
```

### Errores de Conexión
```java
try {
    HttpResponseEntity response = builder.get();
} catch (RuntimeException e) {
    // Timeout, conexión rechazada, DNS failure, etc.
    // El error se loguea automáticamente y se captura en memory queue (si está habilitada)
    System.err.println("Request failed: " + e.getMessage());
}
```

## Configuración del Connector

En `AppConfiguration.java`:

```java
@Produces
@ApplicationScoped
public HttpClientConnector httpClientConnector() {
    return new HttpClientConnector(
        Duration.ofSeconds(5),    // default request timeout
        Duration.ofSeconds(1)     // connect timeout
    );
}
```

El `HttpClientConnector` usa internamente:
- `java.net.http.HttpClient` (JDK 11+)
- HTTP/1.1 por defecto
- Connection pooling automático
- Keep-alive nativo
- TLS verificado
