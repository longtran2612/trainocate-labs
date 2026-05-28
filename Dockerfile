# Global build args — must be declared before the first FROM
# so they can be used inside FROM instructions across all stages
ARG JRE_VERSION=25

# Stage 1 — OpenTelemetry Java Agent
FROM ghcr.io/open-telemetry/opentelemetry-java-instrumentation/opentelemetry-javaagent:2.14.0 AS otel-agent

# Stage 2 — Runtime
FROM eclipse-temurin:${JRE_VERSION}-jre-alpine

# Copy OTel agent from stage 1
COPY --from=otel-agent /javaagent.jar /app/otel-javaagent.jar

ARG SERVICE_NAME
ARG SERVICE_PORT

WORKDIR /app

COPY ${SERVICE_NAME}/build/libs/*.jar app.jar

EXPOSE ${SERVICE_PORT}

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -javaagent:/app/otel-javaagent.jar -jar app.jar"]
