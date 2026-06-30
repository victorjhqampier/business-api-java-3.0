FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy Maven metadata first to maximize Docker layer reuse for dependencies.
COPY pom.xml .
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/http-client-builder/pom.xml infrastructure/http-client-builder/pom.xml
COPY infrastructure/fake-api-infra/pom.xml infrastructure/fake-api-infra/pom.xml
COPY infrastructure/redis-infrastructure/pom.xml infrastructure/redis-infrastructure/pom.xml
COPY presentation/eventlistener/pom.xml presentation/eventlistener/pom.xml
COPY presentation/businessapi/pom.xml presentation/businessapi/pom.xml
COPY presentation/businessapi/mvnw presentation/businessapi/mvnw

RUN chmod +x presentation/businessapi/mvnw
RUN ./presentation/businessapi/mvnw -B -f pom.xml -pl presentation/businessapi -am dependency:go-offline

COPY domain domain
COPY application application
COPY infrastructure infrastructure
COPY presentation presentation

RUN chmod +x presentation/businessapi/mvnw
RUN ./presentation/businessapi/mvnw -B -f pom.xml -pl presentation/businessapi -am package -DskipTests=true -DskipITs=true

FROM public.ecr.aws/amazoncorretto/amazoncorretto:21 AS runtime

WORKDIR /deployments

RUN useradd --system --uid 1001 --create-home --home-dir /home/quarkus quarkus

COPY --from=build /workspace/presentation/businessapi/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/presentation/businessapi/target/quarkus-app/*.jar ./
COPY --from=build /workspace/presentation/businessapi/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/presentation/businessapi/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

USER 1001

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
