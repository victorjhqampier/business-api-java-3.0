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

## Build y ejecucion con Docker

La imagen Docker usa multi-stage build: un stage compila el reactor Maven multi-modulo y el stage final copia solo `presentation/businessapi/target/quarkus-app`. La imagen runtime usa Amazon Corretto 21 para despliegues en AWS ECS/EKS.

Build local desde la raiz del proyecto:

```bash
docker build -t businessapi:local .
```

Run local:

```bash
docker run --rm -p 8080:8080 \
  -e REDISDATABASE_HOST=192.168.3.204 \
  -e REDISDATABASE_PASSWD='Sysadmin123++' \
  -e REDISDATABASE_DATABASE=0 \
  -e REDISDATABASE_SSL=false \
  -e EXAMPLE_HOST_BASE=https://jsonplaceholder.typicode.com \
  -e EXAMPLE_TITLE_BASE=https://fakerapi.it \
  -e HTTP_CLIENT_REQUEST_TIMEOUT=5 \
  -e HTTP_CLIENT_CONNECT_TIMEOUT=1 \
  businessapi:local
```

Health check:

```bash
curl http://localhost:8080/health
```

Publicar en Amazon ECR:

```bash
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPOSITORY=businessapi
IMAGE_TAG=latest

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$ECR_REPOSITORY" \
  || aws ecr create-repository --region "$AWS_REGION" --repository-name "$ECR_REPOSITORY"

docker tag businessapi:local "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
docker push "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
```

Para ECS/EKS, configurar variables sensibles con Secrets/Parameter Store/Kubernetes Secrets. No incluir `.env` dentro de la imagen.

## Create .env archive in businessapi
```bash
.../business-api-java-3.0/presentation/businessapi/.env
```

```bash
    REDISDATABASE_HOST=192.168.3.204
    REDISDATABASE_PASSWD=Sysadmin123++
    REDISDATABASE_DATABASE=0
    REDISDATABASE_SSL=false

    EXAMPLE_HOST_BASE=https://jsonplaceholder.typicode.com
    EXAMPLE_TITLE_BASE=https://fakerapi.it

    # HTTP client timeouts in seconds. Defaults in code are 5 and 1.
    HTTP_CLIENT_REQUEST_TIMEOUT=5
    HTTP_CLIENT_CONNECT_TIMEOUT=1
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
2. Definir interfaces de salida en `domain/interfaces/` y sus adaptadores en `infrastructure`.
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
- **CDI Producers**: Todo `@Produces` que entregue un recurso compartido debe declarar scope explicito (`@ApplicationScoped`). `@Produces` solo no equivale a singleton.
- **Evitar asignaciones innecesarias**: Reflection, reconstruccion de reglas, regex compilation, streams en hot path y logs de warning en flujos esperados (4xx).
- **Hilos Virtuales (Java 21)**: Usar `@RunOnVirtualThread` solo en presentation. `application` expone puertos imperativos con `EasyResult<T>` directo; usa `CompletableFuture` internamente solo para paralelismo real. `infrastructure` puede usar `ExecutorService` virtual para I/O bloqueante.
- **Hot Path optimizado**: Preferir loops tradicionales sobre streams cuando se transforman listas de errores o payloads en rutas calientes (422, validación).
- **Configuracion runtime**: Redis, hosts externos y timeouts se configuran con env vars/secrets. No usar `application.properties` para solucionar secretos o configuracion de despliegue.
- **HTTP builder**: Usar logger del caller, emitir log estructurado solo para `statusCode != 200` y evitar cachear respuestas de error como datos validos.
- **Errores globales**: Errores no capturados por controllers deben responder con el wrapper estandar mediante `GlobalExceptionMapper`.
- **Docker AWS**: Mantener Docker multi-stage desde el reactor Maven raiz, runtime Amazon Corretto 21 y `.env` fuera de la imagen.

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
    EasyResult<T> result = useCase.getDataAsync(...);
}

// Application: paralelismo interno solo cuando hay llamadas independientes
CompletableFuture.allOf(task1, task2)
    .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
    .join();
return mapResult(task1.join(), task2.join());
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

En casos de uso, no envolver una llamada que ya retorna `CompletableFuture` dentro de `supplyAsync` solo para usar virtual threads. Eso agrega overhead. El patron recomendado es que presentation ejecute el caso de uso imperativo sobre virtual threads, infrastructure exponga operaciones async cuando haga I/O y application use `CompletableFuture` solo para coordinar paralelismo real.

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
