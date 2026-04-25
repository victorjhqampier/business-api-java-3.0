FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace/Presentation/businessapi

# Copy dependency metadata first to improve Docker layer caching.
COPY Presentation/businessapi/.mvn .mvn
COPY Presentation/businessapi/mvnw .
COPY Presentation/businessapi/pom.xml .

RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY Presentation/businessapi/src src

RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /deployments

RUN groupadd --system quarkus \
    && useradd --system --gid quarkus --create-home --home-dir /home/quarkus quarkus

COPY --from=build /workspace/Presentation/businessapi/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/Presentation/businessapi/target/quarkus-app/*.jar ./
COPY --from=build /workspace/Presentation/businessapi/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/Presentation/businessapi/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"

USER quarkus

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
