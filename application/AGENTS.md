# application/AGENTS.md

Application orquesta casos de uso, validaciones de aplicacion y adaptadores internos. Depende de `domain`. No conoce `presentation` ni `infrastructure`.

## Estructura

```
com/arify/application/
├── ApplicationSetting.java     # Placeholder (private constructor, sin logica)
├── adapters/                   # DTOs de entrada/salida del caso de uso (Java records)
│   ├── RetrieveExampleAdapter
│   └── ExecuteExampleTwoAdapter
├── ports/                      # Interfaces que describen capacidades
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
- Puertos son interfaces; metodos publicos maximo 3 parametros (si mas, usar adapter object).
- Validacion con `FluentValidationExecutor.validate(traceIdentifier)` devuelve `List<ValidationResultAdapter>`.
- Excepciones solo para errores inesperados; flujos esperados usan `EasyResult`.
- `ApplicationSetting` no debe contener logica auxiliar ni helpers.

## Prohibido

- Construir respuestas HTTP.
- Llamar HTTP, SQL, Kafka, SQS, storage directamente.
- Conocer DTOs de proveedores externos.
- Usar tipos de `presentation` o `infrastructure`.
- Crear carpetas principales nuevas sin decision explicita.
- Registrar datos sensibles innecesarios.
- Try/catch vacios o sin mapeo a resultado.
