# presentation/businessapi/AGENTS.md

Capa de presentación: controladores REST, handlers, helpers y **Composition Root**. Es la única capa donde se permite usar Quarkus/Jakarta/CDI.

## Estructura

```
src/main/java/com/arify/
├── config/
│   └── AppConfiguration.java      # Composition Root (wiring CDI)
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

## Composition Root (AppConfiguration)

**Regla de oro**: El wiring de dependencias debe hacerse **exclusivamente** en `AppConfiguration.java`. Aquí se decide qué implementaciones concretas usar y cómo construir los casos de uso.

### Patrón obligatorio:

```java
@ApplicationScoped
public class AppConfiguration {
    
    // 1. Crear singletons de infraestructura
    @Produces
    @ApplicationScoped
    public HttpClientConnector httpClientConnector() {
        return new HttpClientConnector(Duration.ofSeconds(5), Duration.ofSeconds(1));
    }
    
    // 2. Virtual Thread Executor (Java 21 nativo)
    @Produces
    @ApplicationScoped
    @Named("virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    // 3. Lifecycle management
    public void shutdownVirtualThreadExecutor(
            @Disposes @Named("virtualThreadExecutor") ExecutorService executor) {
        executor.shutdown();
    }
    
    // 4. Casos de uso (inyectar dependencias)
    @Produces
    @ApplicationScoped
    public ExamplePort exampleUseCase(
            IFakeApiInfrastructure fakeApiInfrastructure,
            @Named("virtualThreadExecutor") ExecutorService executor) {
        return new ExampleUseCase(fakeApiInfrastructure, executor);
    }
}
```

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

4. **Virtual Threads**: Usar `@RunOnVirtualThread` en endpoints I/O-bound:
   ```java
   @POST
   @Path("/{customer_id}/create")
   @RunOnVirtualThread
   public Response postDataAsync(...) {
       // El .join() aquí es idiomático porque estamos en un virtual thread
       EasyResult<CreateExampleAdapter> result = exampleUseCase.getDataAsync(...).join();
       // ...
   }
   ```

5. **CancellationToken**: Crear el token en presentation porque aquí nace el request HTTP:
   ```java
   CancellationToken cancellationToken = CancellationToken.withTimeout(HTTP_TIMEOUT);
   ```

6. **Manejo de excepciones**: Capturar `CompletionException` para timeouts y `Exception` para errores inesperados. Registrar en nivel apropiado (SEVERE para errores, FINE para validaciones esperadas).

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
