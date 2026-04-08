# -------- Build stage --------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Gradle wrapper + config
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

# Ensure wrapper is executable
RUN chmod +x gradlew

# Cache dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew build -x test --no-daemon

# -------- Runtime stage --------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy built JAR
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]