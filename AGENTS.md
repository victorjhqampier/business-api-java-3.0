# AGENTS.md

Java 21 + Quarkus 3.34.1 multi-module Maven project (`com.arify`).

## Arquitectura

```
domain → nada
application → domain
infrastructure/* → domain (y otros infra cuando aplique)
presentation/businessapi → application
```

Regla central: `application` orquesta casos de uso y llama a **interfaces definidas en domain** que implementa `infrastructure`. `application/ports` contiene solo puertos de entrada que usa `presentation` para invocar casos de uso. En `domain` no se llaman ports; se llaman interfaces.

6 modulos: `domain`, `application`, `infrastructure/fake-api-infra`, `infrastructure/http-client-builder`, `presentation/eventlistener`, `presentation/businessapi`.

| Modulo | Layout | Dependencias Maven |
|--------|--------|-------------------|
| `domain` | `sourceDirectory=${project.basedir}` (flat, sin `src/main/java/`) | ninguna |
| `application` | idem flat | `domain` |
| `infrastructure/fake-api-infra` | idem flat | `domain`, otros infra necesarios |
| `infrastructure/http-client-builder` | idem flat | `domain`, `jackson-databind` |
| `presentation/eventlistener` | idem flat | `application` si necesita orquestacion; evitar depender directo de infra |
| `presentation/businessapi` | `src/main/java/` estandar | `application`, Quarkus BOM; composition root puede incluir infra para wiring CDI |

## Enfoque de rendimiento

Estas APIs deben estar orientadas a cargas **data-intensive** en primer lugar y **processing-intensive** en segundo lugar. La prioridad es mover, validar, transformar y responder datos con baja latencia y bajo desperdicio de memoria; el trabajo CPU pesado se atiende despues y debe aislarse para no degradar el camino I/O.

### Mandatos de Alto Rendimiento (OBLIGATORIOS)

**1. Objetos Estáticos (Singletons)**
- `Logger`: SIEMPRE `private static final Logger LOGGER = Logger.getLogger(...)`. Instanciarlo por request es un error de arquitectura.
- `ObjectMapper` (Jackson): SIEMPRE `private static final ObjectMapper`. Registrar módulos (`JavaTimeModule`) una sola vez al inicializar.
- Validadores (`AbstractValidator<T>`): SIEMPRE `private static final` o `@ApplicationScoped`. Nunca reconstruir reglas ni metadata por request.
- HTTP Clients (`HttpClient`, `HttpClientConnector`): SIEMPRE `@ApplicationScoped` (Singleton CDI). No crear instancias nuevas por petición.
- ExecutorService: SIEMPRE `@ApplicationScoped` con lifecycle management (`@Disposes` para shutdown limpio).

**2. Protocolo de Hilos Virtuales (Java 21)**
- **Presentation Layer**: Usar `@RunOnVirtualThread` en endpoints REST. Permite usar `.join()` de forma idiomática sin bloquear platform threads.
- **Application Layer**: Recibir `ExecutorService` por constructor (inyectado desde Composition Root). Usar `CompletableFuture.*Async(..., executor)` para continuaciones. **PROHIBIDO** importar anotaciones Quarkus/Jakarta en esta capa.
- **Composition Root** (`AppConfiguration`): Usar `Executors.newVirtualThreadPerTaskExecutor()` para crear el pool de hilos virtuales. Es la forma nativa de Java 21 para I/O-bound.
- **Benefit**: Con el mismo hardware, pasar de cientos de conexiones simultáneas a decenas de miles. Los hilos virtuales son minúsculos (KB vs MB).

**3. Hot Path (Rutas Calientes)**
- **Loops vs Streams**: En métodos que transforman listas de errores de validación o payloads grandes, preferir loops tradicionales (`for`, `ArrayList.add()`) sobre `stream().map().toList()`. Esto minimiza la basura en el GC.
- **Ejemplo Correcto**:
  ```java
  List<FieldErrorInternalModel> errors = new ArrayList<>(errorList.size());
  for (ValidationResultAdapter error : errorList) {
      errors.add(new FieldErrorInternalModel(error.code(), error.message(), error.field()));
  }
  ```
- **Ejemplo Incorrecto** (en hot path):
  ```java
  List<FieldErrorInternalModel> errors = errorList.stream()
      .map(error -> new FieldErrorInternalModel(...))
      .toList(); // Crea intermediarios innecesarios
  ```

**4. Scopes CDI**
- Controllers: `@ApplicationScoped` (Singleton). Solo si el controller NO tiene estado mutable entre requests.
- Use Cases: Inyectar como `@ApplicationScoped`. Los casos de uso no deben tener estado que varíe entre peticiones.
- Handlers/Helpers que NO tienen dependencias inyectables: Clase con constructor privado y métodos `static`.

**5. Manejo de Excepciones (Arquitectura de Flujo)**
- **PROHIBIDO usar `try-catch` en `infrastructure` y `application`**: Las excepciones deben fluir naturalmente hacia `presentation`.
- **`infrastructure`**: NO atrapar `JsonProcessingException`, `IOException`, `TimeoutException` ni errores de parsing/HTTP. Dejarlas propagarse.
- **`application`**: NO atrapar excepciones de infraestructura. Solo validar datos de negocio con `EasyResult`.
- **`presentation`**: Única capa autorizada para `try-catch`. Los controladores capturan todas las excepciones (timeout, parsing, validación, infraestructura) y las mapean a respuestas HTTP apropiadas.
- **Beneficio**: Arquitectura clara de responsabilidades. Infraestructura y aplicación se mantienen puras, sin lógica de manejo de errores que oscurezca el happy path. La presentación actúa como "safety net" global.
- **Excepción a la regla**: Solo si una excepción debe enriquecerse con contexto específico de infraestructura antes de propagarse (ej: agregar traceId), se permite `catch + throw` (re-throw con contexto adicional), NUNCA `catch` silencioso o con retorno de valor por defecto.

### Reglas de Medición

- Medir antes de concluir. Una medicion de Postman incluye cliente, red local, dispatch HTTP, Jackson, logs, trazas, warmup y respuesta; no equivale a validacion pura.
- Medir endpoints en modo jar o produccion, despues de warmup. La primera request puede incluir lazy init/JIT y no representa latencia estable.
- Para micro-costos usar mediciones dentro de JVM o JMH cuando aplique; para endpoints usar `curl`, `wrk`, `hey`, `ab` u otra herramienta repetible.
- Optimizar primero el camino data-intensive: serializacion/deserializacion, HTTP clients, ObjectMapper, validadores, trazas, colas, buffers, logs y asignaciones por request.
- Los flujos esperados como 4xx/empty/no-content no deben escribir logs `warning`/`severe` por request; usar `fine/debug` o metricas.
- Para I/O-bound usar virtual threads en presentation/background services cuando aplique; para CPU-bound usar ejecutores acotados y no mezclar trabajo CPU pesado con el loop de request.
- Mantener backpressure: colas con capacidad, timeouts, cancelacion y limites de payload son parte del diseno data-intensive.

### Hallazgo Base de Rendimiento (Verificado)

- El endpoint `POST /business-api-b/v1/no-bian/{customer_id}/create` fue medido en jar local con payload invalido 422. La primera request fria rondo ~97 ms por warmup/lazy init; requests calientes quedaron alrededor de 2.35-2.70 ms, con un outlier de 4.89 ms.
- La validacion pura de campos es **sub-milisegundo**; una lectura de 32-53 ms en Postman normalmente apunta a medicion de request completa, dev mode, warmup, logging, trazas o asignaciones innecesarias.
- Se corrigio el camino caliente para reutilizar validadores, evitar reconstruir reglas/reflection por request, reutilizar `ObjectMapper` de trazas y bajar logs esperados de validacion a nivel fino.
- **Resultado**: Con hilos virtuales + singletons + hot path optimizado, el sistema puede manejar 10-100x más tráfico concurrente con el mismo hardware que un diseño tradicional con platform threads.

## Quirk: package vs file path

`infrastructure/*`, `presentation/eventlistener`, `domain/` y `application/` usan `sourceDirectory=${project.basedir}`. Archivos bajo `infrastructure/fake-api-infra/com/arify/fakeapiinfra/` declaran `package com.arify.application` o `com.arify.domain.entities`. **No corregir.** No mover archivos ni cambiar packages sin decision explicita.

## Comandos

```bash
# Maven wrapper (solo aqui existe)
./presentation/businessapi/mvnw

# Build completo (salta ITs)
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true

# Dev mode (hot reload)
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev

# Package jar
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am clean package
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar

# Tests unitarios (surefire, @QuarkusTest)
mvn test

# Tests de integracion (failsafe, @QuarkusIntegrationTest)
mvn verify -DskipITs=false

# Test especifico
mvn test -Dtest=HealthControllerTest

# Native image (GraalVM) — activa perfil native, fuerza ITs
mvn package -Pnative -DskipTests
```

## Test quirks

- Solos tests existen en `presentation/businessapi/src/test/`.
- `@QuarkusIntegrationTest` (HealthControllerIT) **extiende** `@QuarkusTest` (HealthControllerTest) con cuerpo vacio — hereda todos los metodos. Patron a replicar.
- ITs se saltan por defecto (`skipITs=true`). Ejecutar con `mvn verify -DskipITs=false`.

## Convenciones

- DTOs, entidades de dominio y adapters: **Java records**.
- Casos de uso retornan `EasyResult<T>` (success/failure/empty). Flujos esperados usan `EasyResult`, no excepciones.
- Puertos de entrada: interfaces en `application/ports/`. Casos de uso las implementan y `presentation` las consume.
- Interfaces de dominio: contratos en `domain/interfaces/` que `application` consume e `infrastructure` implementa. No llamarlas ports.
- Validacion: `FluentValidationExecutor`.
- **Validadores**: SIEMPRE reutilizar instancias `private static final` en casos de uso o `@ApplicationScoped` cuando sea necesario. Ejemplo:
  ```java
  private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
  // Uso:
  List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(trace, TRACE_IDENTIFIER_VALIDATOR);
  ```
- **Integración con CacheLibraryService**: 
  - Usar tipos concretos en `resolveAsync(..., ConcreteType.class, ...)` para evitar type erasure y casts manuales.
  - Manejar `Optional` de infraestructura inline: `loader -> infra.callAsync(...).thenApply(opt -> opt.orElse(null))`.
  - Evitar clases "Support" o métodos privados para transformaciones simples; mantener el caso de uso autocontenido.
- **Virtual threads**: 
  - En `presentation`: usar `@RunOnVirtualThread` en endpoints REST.
  - En `application`: usar solo Java nativo (`ExecutorService` inyectado, `CompletableFuture.*Async(..., executor)`).
  - **PROHIBIDO**: importar anotaciones Quarkus/Jakarta en `application` o `domain`.
- **Loggers**: SIEMPRE `private static final Logger LOGGER = Logger.getLogger(...)` para minimizar overhead.
- Java 21 validado por `maven-enforcer-plugin`.
- CORS: solo `arify.com`, metodos GET,POST,PUT,DELETE.
- Dockerfile usa `presentation/businessapi` (lowercase), compatible con filesystems case-sensitive.

## Endpoints base

| Ruta | Descripcion |
|------|-------------|
| `GET /` | Info del servicio |
| `GET /ping` | Ping/pong |
| `GET /health` | Health chequeado |
| `GET /health/live` | Liveness |
| `GET /health/ready` | Readiness |
| `/q/swagger-ui` | Swagger UI |
| `/openapi` | OpenAPI spec |

## Capas locales

Si editas bajo `domain/` o `application/`, lee su `AGENTS.md` local — contienen reglas estrictas:
- **domain**: entidades e interfaces puras, sin anotaciones ni dependencias de framework.
- **application**: casos de uso, orquesta interfaces de `domain`, no conoce HTTP/infrastructure/presentation, prohibido llamar servicios externos directamente.

## No modificar sin instruccion explicita

- `.github/` (hooks de AI assistant)
- `ConfigForIATools/**`, `docs/**`, `env/**` (si existen)
- `opencode.json` (no existe aun en el repo)
- Packages y sourceDirectory de modulos infrastructure
