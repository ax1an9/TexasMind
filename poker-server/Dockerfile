# -- build --
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml ./
COPY poker-core/pom.xml poker-core/
COPY poker-ai/pom.xml poker-ai/
COPY poker-app/pom.xml poker-app/
COPY poker-common/pom.xml poker-common/
COPY poker-server/pom.xml poker-server/
# cache dependencies
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn package -pl poker-server -am -DskipTests -B

# -- run --
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/poker-server/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
