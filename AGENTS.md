# AGENTS.md

Java 21 + Quarkus 3.34.1 multi-module Maven project (`com.arify`).

## Modules

Parent POM at `./pom.xml` defines 7 modules. Maven wrapper only at `./presentation/businessapi/mvnw`.

| Module | Layout | Depends on |
|--------|--------|-----------|
| `domain` | flat (`sourceDirectory=${project.basedir}`) | — |
| `application` | flat | `domain` |
| `infrastructure/http-client-builder` | flat | `domain`, `jackson-databind` |
| `infrastructure/fake-api-infra` | flat | `domain`, `http-client-builder` |
| `infrastructure/redis-infrastructure` | flat | `domain`, `jedis`, `jackson-databind` |
| `presentation/eventlistener` | flat | `domain`, Quarkus BOM |
| `presentation/businessapi` | `src/main/java/` standard | `application`, all infra modules, Quarkus BOM |

## Architecture

```
domain → (nothing)
application → domain
infrastructure/* → domain (and other infra when needed)
presentation/businessapi → application
```

- `application` orchestrates use cases via **domain interfaces** (`domain/interfaces/`). `infrastructure` implements them.
- `application/ports/` = input ports consumed by `presentation`. Do not put output contracts there.
- All use cases return `EasyResult<T>` (success/failure/empty). No exceptions for expected flows.

## Package vs file path quirk

`domain/`, `application/`, `infrastructure/*/`, `presentation/eventlistener/` use `sourceDirectory=${project.basedir}` — no `src/main/java/`. Files at `infrastructure/fake-api-infra/com/arify/fakeapiinfra/` can declare `package com.arify.application`. **Do not "fix" this.** Do not move files or change packages.

## Commands

```bash
# Build all (skip ITs)
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true

# Dev mode (hot reload)
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev

# Package + run jar
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am clean package
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar

# Unit tests (surefire)
mvn test

# Integration tests (failsafe) — skipped by default
mvn verify -DskipITs=false

# Single test
mvn test -Dtest=HealthControllerTest

# Native image (GraalVM)
mvn package -Pnative -DskipTests
```

## Test quirks

- All tests in `presentation/businessapi/src/test/`.
- `@QuarkusIntegrationTest` (e.g. `HealthControllerIT`) **extends** `@QuarkusTest` (e.g. `HealthControllerTest`) with empty body — inherits all test methods. Replicate this pattern.
- ITs skip by default (`skipITs=true`). Run with `mvn verify -DskipITs=false`.

## Conventions (mandatory)

- **DTOs, domain entities, adapters**: Java records.
- **FluentValidationExecutor**: validators are `private static final` or `@ApplicationScoped` — never reconstructed per request.
- **Loggers**: `private static final Logger LOGGER = Logger.getLogger(...)` — never per-instance.
- **ObjectMapper**: `private static final` with `JavaTimeModule` registered once.
- **Virtual threads**: `@RunOnVirtualThread` on REST endpoints (presentation only). `application` receives `ExecutorService` by constructor, uses `CompletableFuture.*Async(..., executor)`. **Prohibited**: Quarkus/Jakarta annotations in `application` or `domain`.
- **No try-catch in `infrastructure` or `application`**: Exceptions flow to `presentation` which maps them to HTTP. Exception: catch + re-throw with extra context (e.g. traceId) allowed in infrastructure.
- **Hot path**: Prefer `for` loops over `stream().map().toList()` when transforming validation error lists or large payloads.
- **Controllers**: `@ApplicationScoped`, stateless.

## Composition Root (4 classes)

Actual wiring is in `presentation/businessapi/src/main/java/com/arify/config/`:

| Class | Responsibility |
|-------|---------------|
| `GlobalStartUp` | Technical resources (ExecutorService, HttpClientConnector, JedisPooled, MemoryQueue) |
| `InfrastructureStartUp` | Domain adapters (FakeApiCommand, RedisCacheInfrastructure) |
| `ApplicationStartUp` | Use cases and services (CacheLibraryService, ExampleUseCase) |
| `PresentationStartUp` | Lifecycle hooks (background listeners, startup/shutdown) |

## Per-module AGENTS.md

Each module has its own `AGENTS.md` with stricter rules — read before editing:
- `domain/AGENTS.md` — pure entities/interfaces, no framework annotations
- `application/AGENTS.md` — use case rules, validation, executor usage patterns
- `infrastructure/AGENTS.md` — wiring principles, `*Starting.java` factories, env var priority
- `presentation/businessapi/AGENTS.md` — controller patterns, REST conventions

## Do not modify

- `.github/` — AI assistant hooks
- `docs/` — reference Dockerfiles
- Packages or `sourceDirectory` of infrastructure modules
