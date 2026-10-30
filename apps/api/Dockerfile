# syntax=docker/dockerfile:1

FROM maven@sha256:8b2f036477a5bc9fbeb16cfb7301c484d7fff727b1c4907301ac665526bd7a8e AS build
# maven:3.9.12-eclipse-temurin-21-alpine
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
# eclipse-temurin:21-jre-alpine (21.0.13, Alpine 3.24)
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 warroom \
    && adduser -S -u 10001 -G warroom -h /app warroom
WORKDIR /app
COPY --from=build --chown=warroom:warroom /workspace/target/warroom-api-*.jar /app/app.jar
USER warroom
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
