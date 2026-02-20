FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# JAVA_OPTS는 docker-compose.yml에서 단일 관리 (중복 방지)
ENTRYPOINT ["sh", "-c", "mkdir -p /data/storage/.tmp && java ${JAVA_OPTS:-} -jar app.jar"]
