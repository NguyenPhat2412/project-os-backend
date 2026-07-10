FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY . .
RUN mvn -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE
COPY --from=build "/workspace/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar" app.jar
RUN addgroup -S app && adduser -S app -G app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
