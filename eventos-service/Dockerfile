# Se construye desde la raíz del repositorio para poder incluir common-library:
#   docker build -f eventos-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY common-library ./common-library
RUN mvn -B -f common-library/pom.xml clean install -DskipTests
COPY eventos-service ./eventos-service
RUN mvn -B -f eventos-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/eventos-service/target/*.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app.jar"]
