FROM openjdk:17-alpine
RUN mkdir -p /app/
ADD target/helloworld-0.0.1-SNAPSHOT.jar /app/app.jar
# ENTRYPOINT ["java", "-Dotel.traces.exporter=logging", "-Dotel.metrics.exporter=logging", "-Dotel.logs.exporter=logging", "-jar", "/app/app.jar"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]