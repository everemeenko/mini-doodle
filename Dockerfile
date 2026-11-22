# Stage 1: Build and test the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY mvnw* ./
COPY .mvn* ./.mvn/

RUN if [ -f "./mvnw" ] && [ -f ".mvn/wrapper/maven-wrapper.properties" ]; then \
        chmod +x ./mvnw && MVN_CMD="./mvnw"; \
    else \
        apk add --no-cache maven && MVN_CMD="mvn"; \
    fi && \
    $MVN_CMD dependency:go-offline -B

COPY src ./src
RUN if [ -f "./mvnw" ] && [ -f ".mvn/wrapper/maven-wrapper.properties" ]; then \
        MVN_CMD="./mvnw"; \
    else \
        MVN_CMD="mvn"; \
    fi && \
    $MVN_CMD clean package -B

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
