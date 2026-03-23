FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
LABEL authors="4bderrahmane"

RUN useradd -m springuser || true
USER springuser
WORKDIR /home/springuser

ARG JAR_FILE=/workspace/target/*.jar
COPY --from=build ${JAR_FILE} ./app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "./app.jar"]
