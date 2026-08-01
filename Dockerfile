# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user + writable dir for SQLite mismatch traces
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data \
    && chown -R app:app /app

ENV SHADOW_SQLITE_PATH=/app/data/shadow-mismatches.db
ENV SHADOW_ROUTING_PERCENTAGE=100

# Persist mismatch traces across container restarts
VOLUME ["/app/data"]

USER app

COPY --from=build --chown=app:app /workspace/target/llm-shadow-router-*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
