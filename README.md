# Business API Template

Plantilla base para construir microservicios con Java y Quarkus, alineada a convenciones de arquitectura por capas y buenas practicas para equipos.

## Baseline tecnico

- Java 21+
- Maven Wrapper
- Quarkus 3.x
- OpenAPI y Health checks habilitados

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

## Comandos para levantar el servicio

Desde la raiz del proyecto:

```bash
./presentation/businessapi/mvnw -f pom.xml clean install -DskipITs=true
./presentation/businessapi/mvnw -f presentation/businessapi/pom.xml quarkus:dev
# Optinal qwith reload
mvn -f pom.xml io.quarkus.platform:quarkus-maven-plugin:3.34.1:dev -pl presentation/businessapi -am
```

Build y ejecucion en modo jar:

```bash
./presentation/businessapi/mvnw -f pom.xml -pl presentation/businessapi -am clean package
java -jar presentation/businessapi/target/quarkus-app/quarkus-run.jar
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
2. Definir puertos de salida en application y sus adaptadores en infrastructure.
3. Agregar pruebas unitarias de use cases y pruebas de integracion por endpoint critico.
4. Configurar pipeline CI para ejecutar compile, test y analisis estatico.
