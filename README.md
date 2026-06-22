# Business API Template

Plantilla base para construir microservicios con Java y Quarkus, alineada a convenciones de arquitectura por capas y buenas practicas para equipos.

Este template esta orientado a APIs **data-intensive** primero y **processing-intensive** en segundo lugar. El objetivo principal es mover, validar, transformar y responder datos con baja latencia, baja presion de memoria y control claro de I/O; el procesamiento CPU pesado debe aislarse y medirse aparte.

## Baseline tecnico

- Java 21+
- Maven Wrapper
- Quarkus 3.x
- OpenAPI y Health checks habilitados

## Build y ejecucion en modo jar:

```bash
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am clean package
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar
```

## Swagger UI y OpenAPI

- Swagger UI (local): http://localhost:8080/q/swagger-ui
- OpenAPI JSON (local): http://localhost:8080/openapi

## Endpoints base

- GET /
- GET /ping
- GET /health
- GET /health/live
- GET /health/ready

## Recomendaciones para iniciar un nuevo microservicio

1. Reemplazar nombres Example* por tu contexto de negocio.
2. Definir puertos de salida en application y sus adaptadores en infrastructure.
3. Agregar pruebas unitarias de use cases y pruebas de integracion por endpoint critico.
4. Configurar pipeline CI para ejecutar compile, test y analisis estatico.



## Estructura de capas

- domain: entidades y reglas de dominio puras, sin dependencias de framework.
- application: casos de uso, puertos y contratos internos.
- infrastructure: adaptadores de acceso externo (HTTP clients, persistencia, eventos).
- presentation: controladores REST, handlers y serializacion.

## Convenciones del template

- Un solo idioma en codigo y contratos (ingles recomendado).
- Casos de uso sin semantica HTTP: la capa Presentation mapea errores a status code.
- Contratos REST tipados con DTO/record, evitar respuestas genericas con mapas.
- Version de Java validada en build con Maven Enforcer.
- Pruebas de endpoint como minimo para health/ping.
- **Objetos Singleton obligatorios**: Validadores sin estado, `ObjectMapper`, `Logger`, clientes HTTP/connectors y metadata deben reutilizarse como `private static final` o `@ApplicationScoped`.
- **Evitar asignaciones innecesarias**: Reflection, reconstruccion de reglas, regex compilation, streams en hot path y logs de warning en flujos esperados (4xx).
- **Hilos Virtuales (Java 21)**: Usar `@RunOnVirtualThread` solo en presentation. En application, usar Java nativo (`Executor`/`ExecutorService`, `CompletableFuture.*Async(..., executor)`) para continuaciones o APIs bloqueantes; usar ejecutores acotados para trabajo CPU-bound.
- **Hot Path optimizado**: Preferir loops tradicionales sobre streams cuando se transforman listas de errores o payloads en rutas calientes (422, validación).

## Enfoque data-intensive

Prioridades al crear o modificar endpoints:

1. **Reducir I/O y asignaciones por request**: Serializacion, deserializacion, trazas, colas, buffers y DTOs.
2. **Mantener backpressure**: Timeouts, cancelacion, limites de payload y colas con capacidad.
3. **Reutilizar objetos inicializados**: Validadores, clientes HTTP, mappers y metadata como `private static final` o `@ApplicationScoped`.
4. **Mantener `application` libre**: Sin HTTP, logging operativo, SDKs y detalles de infraestructura.
5. **Aislar trabajo processing-intensive**: Con ejecutores acotados y mediciones separadas.

### Patrones de Alto Rendimiento Implementados:

**Objetos Singleton (Obligatorio)**:
```java
// Logger estático
private static final Logger LOGGER = Logger.getLogger(MyClass.class.getName());

// ObjectMapper estático
private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

// Validadores estáticos
private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
```

**Virtual Threads (Java 21)**:
```java
// Presentation: Usar @RunOnVirtualThread
@POST
@Path("/{id}/create")
@RunOnVirtualThread
public Response postDataAsync(...) {
    EasyResult<T> result = useCase.getDataAsync(...).join(); // .join() es idiomático aquí
}

// Application: Usar ExecutorService inyectado
return CompletableFuture.allOf(task1, task2)
    .thenApplyAsync(ignored -> mapResult(...), myThreadExec);
```

**Hot Path Optimizado**:
```java
// ✓ Correcto: Loop tradicional
List<FieldErrorInternalModel> errors = new ArrayList<>(errorList.size());
for (ValidationResultAdapter error : errorList) {
    errors.add(new FieldErrorInternalModel(error.code(), error.message(), error.field()));
}

// ✗ Evitar: Stream en hot path
List<FieldErrorInternalModel> errors = errorList.stream()
    .map(error -> new FieldErrorInternalModel(...))
    .toList(); // Crea intermediarios
```

En casos de uso, no envolver una llamada que ya retorna `CompletableFuture` dentro de `supplyAsync` solo para usar virtual threads. Eso agrega overhead. El patron recomendado es que infrastructure exponga operaciones no bloqueantes cuando pueda, y que application use el executor inyectado para continuaciones (`thenApplyAsync`, `thenComposeAsync`) o para adaptar trabajo I/O-bound realmente bloqueante.

## Hallazgos de rendimiento

Se analizo el endpoint `POST /business-api-b/v1/no-bian/{customer_id}/create` con payload invalido que retorna 422.

- La validacion pura de campos es **sub-milisegundo**; no estaba costando 32-53 ms.
- Una medicion de Postman representa el request completo: cliente, red local, dispatch HTTP, Jackson, logs, trazas, warmup y respuesta.
- En jar local, la primera request fria rondo ~97 ms por warmup/lazy init/JIT.
- Requests calientes del mismo endpoint quedaron alrededor de **2.35-2.70 ms**, con un outlier de 4.89 ms.
- Se optimizo el camino caliente reutilizando validadores, evitando reconstruir reglas/reflection por request, reutilizando ObjectMapper de trazas y bajando logs esperados de validacion a nivel fino.

### Impacto de las Optimizaciones:

**Con hilos virtuales + singletons + hot path optimizado**, el sistema puede manejar **10-100x más tráfico concurrente** con el mismo hardware que un diseño tradicional con platform threads. Los hilos virtuales son minúsculos (KB vs MB de los platform threads), permitiendo pasar de cientos a decenas de miles de conexiones simultáneas.

Para medir endpoints, preferir modo jar/produccion despues de warmup:

```bash
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am package -DskipTests -DskipITs=true
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar
```

Luego usar una herramienta repetible (`curl`, `wrk`, `hey`, `ab`) y descartar la primera request fria. Para micro-costos dentro de JVM, usar JMH o una medicion aislada solo como diagnostico preliminar.

## Comandos para levantar el servicio

Desde la raiz del proyecto:

```bash
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev
# Optional with reload
mvn -f pom.xml io.quarkus.platform:quarkus-maven-plugin:3.34.1:dev -pl presentation/businessapi -am
```
