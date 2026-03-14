# Build stage
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

RUN mkdir -p /app/data/storage/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]