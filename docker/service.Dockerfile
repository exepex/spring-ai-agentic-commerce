# Runtime image for one Spring Boot service. The jar is built on the host first (see start-demo.sh).
FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 1001 commerce
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar app.jar
USER commerce
# A JVM that ran out of memory exits, so the container is restarted instead of limping on.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
