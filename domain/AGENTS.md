# domain/AGENTS.md

Domain es el nucleo puro: entidades, value objects y contratos del negocio. Sin dependencias de framework, HTTP, storage, SDKs ni otras capas del proyecto.

## Estructura

```
com/arify/domain/entities/
├── ExampleEntity      # Java record puro
└── ExampleMessage     # Java record puro
```

## Reglas

- Entidades son Java records puros, sin anotaciones de framework.
- No depende de ninguna otra capa del proyecto.
- Prohibido: anotaciones JAX-RS/Jakarta/Quarkus, referencias a `application`/`infrastructure`/`presentation`, drivers, SDKs cloud, HTTP clients, secrets o configuracion de ambiente.
- Nombres en ingles: clases PascalCase, metodos/variables camelCase.
