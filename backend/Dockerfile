# syntax=docker/dockerfile:1
# Backend image: Spring Boot 3.4.5 + Java 21 LTS.
# Build from the repository root:
#   docker build -t tvc-backend:dev backend
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
 && addgroup -S tvc && adduser -S -G tvc -u 10001 tvc
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER tvc
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=10 \
  CMD curl -fsS http://localhost:8080/api/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
