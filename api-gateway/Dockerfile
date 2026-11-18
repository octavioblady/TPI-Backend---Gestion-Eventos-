# Se construye desde la raíz del repositorio para poder incluir common-library:
#   docker build -f api-gateway/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY common-library ./common-library
RUN mvn -B -f common-library/pom.xml clean install -DskipTests
COPY api-gateway ./api-gateway
RUN mvn -B -f api-gateway/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/api-gateway/target/*.jar /app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app.jar"]
