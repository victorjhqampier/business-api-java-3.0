# AGENTS.md

Java 21 + Quarkus 3.34.1 multi-module Maven project (`com.arify`).

## Arquitectura

```
presentation/businessapi → application → domain
infrastructure/* → domain (por convencion, sin dependency declarada en pom.xml)
```

5 modulos: `domain`, `application`, `infrastructure/fake-api-infra`, `infrastructure/http-client-builder`, `presentation/businessapi`.

| Modulo | Layout | Dependencias Maven |
|--------|--------|-------------------|
| `domain` | `sourceDirectory=${project.basedir}` (flat, sin `src/main/java/`) | ninguna |
| `application` | idem flat | `domain` |
| `infrastructure/*` | idem flat | **ninguna** (usa packages de `domain`/`application` por convencion) |
| `presentation/businessapi` | `src/main/java/` estandar | `application`, Quarkus BOM |

## Quirk: package vs file path

`infrastructure/*` y `domain/`/`application/` usan `sourceDirectory=${project.basedir}`. Archivos bajo `infrastructure/fake-api-infra/com/arify/fakeapiinfra/` declaran `package com.arify.application` o `com.arify.domain.entities`. **No corregir.** No mover archivos ni cambiar packages sin decision explicita.

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
- Puertos: interfaces en `application/ports/`. Casos de uso las implementan o son standalone.
- Validacion: `FluentValidationExecutor`.
- Virtual threads: Quarkus se usa solo en presentation; usar `@RunOnVirtualThread` en endpoints o `Thread.ofVirtual()` en background services cuando aplique.
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
- **domain**: entidades puras, sin anotaciones ni dependencias de framework.
- **application**: casos de uso, no conoce HTTP/infrastructure/presentation, prohibido llamar servicios externos directamente.

## No modificar sin instruccion explicita

- `.github/` (hooks de AI assistant)
- `ConfigForIATools/**`, `docs/**`, `env/**` (si existen)
- `opencode.json` (no existe aun en el repo)
- Packages y sourceDirectory de modulos infrastructure
