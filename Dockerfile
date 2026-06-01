# Stage 1: Build the application
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
# Downloader dependencies to cache them in the Docker layer
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run the application
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose server port
EXPOSE 8080

# Run JVM with optimized flags
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
