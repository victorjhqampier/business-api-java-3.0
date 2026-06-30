# presentation/businessapi/AGENTS.md

Capa de presentación: controladores REST, handlers, helpers y **Composition Root**. Es la única capa donde se permite usar Quarkus/Jakarta/CDI.

## Estructura

```
src/main/java/com/arify/
├── startup/                       # Composition Root
│   ├── presentation/              # ThreadSetting, EventListenerSetting
│   ├── infrastructure/            # HttpClientBuilderSetting, RedisSetting, FakeApiSetting
│   └── application/               # ApplicationSetting
├── controllers/
│   └── nonbianexample/
│       └── NoBianExampleController.java
├── handlers/
│   └── MicroserviceTraceHandler.java
├── helpers/
│   └── EasyResponseHelper.java
└── models/
    └── internals/
        ├── FieldErrorInternalModel.java
        └── NoBianResponseModel.java

src/test/java/
└── com/arify/
    └── [Tests unitarios e integración]
```

## Composition Root

El wiring está dividido en clases `*Setting` bajo `com.arify.startup/`:

| Clase | Responsabilidad |
|-------|---------------|
| `ThreadSetting` | `ExecutorService` de virtual threads |
| `HttpClientBuilderSetting` | `HttpClientConnector` compartido |
| `RedisSetting` | `JedisPooled`, cache service e infraestructura Redis |
| `FakeApiSetting` | Adapter `FakeApiCommand` |
| `ApplicationSetting` | Use cases expuestos por puertos de entrada |
| `EventListenerSetting` | Memory queue y listener de eventos |

Cada clase usa `@ApplicationScoped` + `@Produces` + `@Disposes`. Todo metodo `@Produces` que devuelva un recurso compartido debe tener scope explicito, normalmente `@ApplicationScoped`; `@Produces` solo no es singleton.

## Controladores REST

### Principios de alto rendimiento:

1. **Scope**: Usar `@ApplicationScoped` explícitamente. Los controladores deben ser stateless (sin campos mutables entre requests).

2. **Logger estático**:
   ```java
   private static final Logger LOGGER = Logger.getLogger(NoBianExampleController.class.getName());
   ```

3. **Constantes estáticas**:
   ```java
   private static final String OPERATION_NAME = "obtener-cliente";
   private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(9);
   ```

4. **Virtual Threads**: Usar `@RunOnVirtualThread` en endpoints I/O-bound. Los puertos de application retornan `EasyResult<T>` directamente; no hacer `.join()` en presentation para puertos nuevos:
   ```java
   @POST
   @Path("/{customer_id}/create")
   @RunOnVirtualThread
   public Response postDataAsync(...) {
       EasyResult<CreateExampleAdapter> result = exampleUseCase.getDataAsync(...);
       // ...
   }
   ```

5. **CancellationToken**: Crear el token en presentation porque aquí nace el request HTTP:
   ```java
   CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);
   ```

6. **Manejo de excepciones**: Capturar `CancellationException`/`CompletionException` cuando el endpoint necesita trazabilidad por request. Errores no capturados deben quedar cubiertos por `GlobalExceptionMapper` y responder con el wrapper de `EasyResponseHelper`.

7. **Configuracion runtime**: Redis, HTTP clients y endpoints externos se configuran por variables de entorno o MicroProfile Config. No mover secretos ni defaults de despliegue a `application.properties` como solucion.

## Handlers y Helpers

### MicroserviceTraceHandler

**Patrón de optimización aplicado**:
- `ObjectMapper` estático: `private static final ObjectMapper OBJECT_MAPPER = ...`
- `Logger` estático: `private static final Logger LOGGER = ...`
- Instancia creada por request (ligera), pero los objetos pesados son estáticos.

### EasyResponseHelper

**Hot Path optimizado**:
- Usar loops tradicionales en lugar de streams para transformar listas de errores:
  ```java
  List<FieldErrorInternalModel> errors = new ArrayList<>(errorList.size());
  for (ValidationResultAdapter error : errorList) {
      errors.add(new FieldErrorInternalModel(error.code(), error.message(), error.field()));
  }
  ```

### GlobalExceptionMapper

- Debe permanecer en presentation, con `@Provider` y `@ApplicationScoped`.
- Debe responder errores inesperados con el wrapper estandar de `EasyResponseHelper`.
- No debe filtrar detalles internos ni secretos al cliente; loguear el detalle en servidor con `Logger` estatico.

## Tests

### Convenciones:

- Tests unitarios: `@QuarkusTest` (ej. `HealthControllerTest`)
- Tests de integración: `@QuarkusIntegrationTest` (ej. `HealthControllerIT`)
- Pattern: IT debe extender Test con cuerpo vacío para heredar todos los métodos.

### Comandos:

```bash
# Solo tests unitarios
./mvnw test

# Tests de integración
./mvnw verify -DskipITs=false

# Test específico
./mvnw test -Dtest=HealthControllerTest
```

## Prohibido

- Lógica de negocio en controladores (debe estar en `application`).
- Crear instancias de validadores, ObjectMappers o clientes HTTP por request.
- Usar platform threads para I/O-bound (usar virtual threads).
- Logger no estático (instanciarlo por request).
- Importar clases de `domain` directamente (usar `application/ports`).

## Dependencias Maven

Este módulo puede depender de:
- `application` (obligatorio)
- `infrastructure/*` (solo para wiring en Composition Root)
- Quarkus BOM (solo en este módulo)

Los otros módulos (`domain`, `application`, `infrastructure/*`) deben ser Java puro sin Quarkus.
