# Se construye desde la raíz del repositorio para poder incluir common-library:
#   docker build -f recursos-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY common-library ./common-library
RUN mvn -B -f common-library/pom.xml clean install -DskipTests
COPY recursos-service ./recursos-service
RUN mvn -B -f recursos-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/recursos-service/target/*.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
