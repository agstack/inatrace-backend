FROM maven:3.8.5-openjdk-17-slim as build-stage

WORKDIR /src
COPY . .
# CI runs the Testcontainers suite with Docker available before building this image.
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre as package-stage

ARG JAR_FILE=target/*.jar
COPY --from=build-stage /src/${JAR_FILE} ./app.jar

ENTRYPOINT ["java","-jar","app.jar"]
