# syntax=docker/dockerfile:1.7
# LOGYX backend — imagen JVM (Quarkus fast-jar). Uso: docker build -t logyx-backend backend/
# Ver infra/README.md. La caché de BuildKit (/root/.m2) evita bajar dependencias en cada build.

# ---------- build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# Sin tests (corren en CI). El OpenAPI se escribe dentro de target/ (el contrato versionado está en docs/api/).
RUN --mount=type=cache,target=/root/.m2 \
    MAVEN_OPTS="-Xmx1g" ./mvnw -B -q -DskipTests package \
      -Dquarkus.smallrye-openapi.store-schema-directory=target/openapi

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 logyx && adduser -S -D -H -u 10001 -G logyx logyx
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/target/quarkus-app/ /app/
USER 10001:10001
ENV QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/q/health/ready >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
