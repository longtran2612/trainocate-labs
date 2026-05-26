FROM eclipse-temurin:25-jre-alpine

ARG SERVICE_NAME
ARG SERVICE_PORT

WORKDIR /app

COPY ${SERVICE_NAME}/build/libs/*.jar app.jar

EXPOSE ${SERVICE_PORT}

ENV SPRING_PROFILES_ACTIVE=k8s
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
