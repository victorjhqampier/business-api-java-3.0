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
- Excepciones solo para errores inesperados; flujos esperados usan `EasyResult`.
- `ApplicationSetting` no debe contener logica auxiliar ni helpers.

## Prohibido

- Construir respuestas HTTP.
- Llamar HTTP, SQL, Kafka, SQS, storage directamente.
- Conocer DTOs de proveedores externos.
- Usar tipos de `presentation` o `infrastructure`.
- Definir en `application/ports/` interfaces que deba implementar `infrastructure`.
- Crear carpetas principales nuevas sin decision explicita.
- Registrar datos sensibles innecesarios.
- Try/catch vacios o sin mapeo a resultado.
