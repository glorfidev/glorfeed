FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
    && ./mvnw --batch-mode dependency:go-offline

COPY src src

RUN ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN useradd --system --uid 10001 --create-home spring

COPY --from=build --chown=spring:spring /workspace/target/research-service-*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
