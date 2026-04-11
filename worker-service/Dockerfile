FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/worker-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar", \
  "--server.port=${SERVER_PORT:-8081}", \
  "--worker.id=${WORKER_ID:-worker-1}"]
