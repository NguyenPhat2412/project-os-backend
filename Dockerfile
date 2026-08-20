# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/ ./target/
RUN if [ -f ./target/*exec.jar ]; then cp ./target/*exec.jar app.jar; else cp ./target/*SNAPSHOT.jar app.jar; fi && rm -rf ./target
RUN addgroup -S app && adduser -S app -G app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
