FROM eclipse-temurin:26-jdk-alpine AS builder

WORKDIR /app
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/
COPY src/ src/

RUN ./gradlew build --no-daemon -x test

FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080 50051
ENTRYPOINT ["java", "-jar", "app.jar"]
