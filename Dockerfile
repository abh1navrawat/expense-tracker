# Stage 1: Build the application using Eclipse Temurin JDK 17
FROM maven:3.8.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Downloader dependencies to cache them in the Docker layer
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run the application using optimized lightweight Eclipse Temurin JRE 17
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose server port
EXPOSE 8080

# Run JVM with optimized flags
ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
