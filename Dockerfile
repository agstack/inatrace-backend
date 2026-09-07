FROM maven:3.9.12-eclipse-temurin-21 AS build-stage

WORKDIR /src
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre AS package-stage

WORKDIR /app
COPY --from=build-stage /src/target/coffee-backend-*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
