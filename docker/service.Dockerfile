# Runtime image for one Spring Boot service. The jar is built on the host first (see start-demo.sh).
FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 1001 commerce
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar app.jar
USER commerce
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
