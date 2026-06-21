# application/AGENTS.md

Application orquesta casos de uso, validaciones de aplicacion y adaptadores internos. Depende solo de `domain`. No conoce `presentation` ni `infrastructure`.

Application llama interfaces definidas en `domain/interfaces/` cuando necesita capacidades implementadas por infraestructura. No define esas interfaces de salida aqui.

## Estructura

```
com/arify/application/
├── ApplicationSetting.java     # Placeholder (private constructor, sin logica)
├── adapters/                   # DTOs de entrada/salida del caso de uso (Java records)
│   ├── RetrieveExampleAdapter
│   └── ExecuteExampleTwoAdapter
├── ports/                      # Puertos de entrada consumidos por presentation
│   └── ExamplePort
├── usecases/exampleusecase/    # Implementaciones de casos de uso
│   ├── ExampleUseCase          # Implementa ExamplePort
│   └── ExampleCase             # Clase standalone con metodos de caso de uso
└── internals/
    ├── adapters/               # Adapters compartidos
    │   ├── TraceIdentifierAdapter
    │   └── ValidationResultAdapter
    └── executors/              # Logica de validacion y resultados
        ├── EasyResult<T>       # success/failure/empty
        └── FluentValidationExecutor
```

## Patrones

- Casos de uso retornan `EasyResult<T>`: `EasyResult.success(valor)`, `EasyResult.failure(codigo, errores)`, `EasyResult.empty()`.
- Adaptadores son Java records inmutables.
- Puertos en `application/ports/` son solo puertos de entrada; metodos publicos maximo 3 parametros (si mas, usar adapter object).
- Interfaces de salida implementadas por infraestructura viven en `domain/interfaces/`; application las consume por constructor, nunca por clases concretas.
- Validacion con `FluentValidationExecutor.validate(traceIdentifier)` devuelve `List<ValidationResultAdapter>`.
- **Validadores sin estado**: OBLIGATORIO reutilizarlos como `private static final` o `@ApplicationScoped`. No construir validadores, reglas, regex, reflection de lambdas ni metadata en cada request.
- **Patrón obligatorio de validadores estáticos**:
  ```java
  private static final TraceIdentifierAdapterValidator TRACE_IDENTIFIER_VALIDATOR = new TraceIdentifierAdapterValidator();
  private static final ExampleRequestAdapterValidator EXAMPLE_REQUEST_VALIDATOR = new ExampleRequestAdapterValidator();
  
  // Uso en método:
  List<ValidationResultAdapter> errors = FluentValidationExecutor.execute(trace, TRACE_IDENTIFIER_VALIDATOR);
  ```
- Preferir `FluentValidationExecutor.execute(input, validatorInstance)` cuando el validador ya existe. Mantener el overload con `Supplier` solo para compatibilidad o casos no calientes.
- Excepciones solo para errores inesperados; flujos esperados usan `EasyResult`.
- `ApplicationSetting` no debe contener logica auxiliar ni helpers.

## Rendimiento data-intensive

Application debe optimizar primero el camino data-intensive: validar, orquestar, transformar DTOs y coordinar interfaces de dominio con pocas asignaciones y sin I/O directo. El procesamiento CPU pesado queda en segundo lugar y debe aislarse con limites claros.

- No confundir latencia HTTP completa con costo de validacion pura. Postman incluye cliente, red local, dispatch HTTP, serializacion, logs, trazas y warmup.
- Para validar performance, medir en jar/produccion despues de warmup. La primera request puede incluir lazy init/JIT.
- El hallazgo actual del endpoint 422 indica que la validacion pura es sub-milisegundo; las requests calientes en jar local quedaron cerca de 2.35-2.70 ms.
- **Hot Path**: Evitar streams, reflection y objetos temporales en reglas que corren por request cuando un loop simple o instancia reutilizada sea mas barato y claro.
- Mantener casos de uso libres de HTTP, logging operativo, serialization frameworks y detalles de infraestructura. Esos costos pertenecen a presentation/infrastructure.
- **Hilos virtuales en application**: Se expresan solo con Java nativo. Recibir `Executor`/`ExecutorService` por constructor y usar `CompletableFuture.*Async(..., executor)` para continuaciones o trabajo I/O-bound. No usar `@RunOnVirtualThread` ni anotaciones de framework en casos de uso.
- **IMPORTANTE**: No envolver una operacion ya no bloqueante (`CompletableFuture` retornado por infraestructura) en `supplyAsync` solo para "usar virtual threads"; eso agrega overhead. Usar el executor para continuaciones (`thenApplyAsync`, `thenComposeAsync`) o para adaptar APIs bloqueantes.
- **Ejemplo correcto de uso de executor**:
  ```java
  // Infrastructure retorna CompletableFuture (ya no bloqueante)
  CompletableFuture<Optional<FakeApiEntity>> result1Task = fakeApi.getUserAsync(id, token);
  CompletableFuture<Optional<FakeApiEntity>> result2Task = fakeApi.getTitleAsync(id, token);
  
  // Usar executor solo para continuaciones, NO para envolver
  return CompletableFuture.allOf(result1Task, result2Task)
      .orTimeout(token.remainingTimeout().toMillis(), TimeUnit.MILLISECONDS)
      .thenApplyAsync(ignored -> {
          // Transformación de datos en virtual thread
          return mapToResult(result1Task.join(), result2Task.join());
      }, myThreadExec);
  ```

## Prohibido

- Construir respuestas HTTP.
- Llamar HTTP, SQL, Kafka, SQS, storage directamente.
- Conocer DTOs de proveedores externos.
- Usar tipos de `presentation` o `infrastructure`.
- Definir en `application/ports/` interfaces que deba implementar `infrastructure`.
- Crear carpetas principales nuevas sin decision explicita.
- Registrar datos sensibles innecesarios.
- Try/catch vacios o sin mapeo a resultado.
