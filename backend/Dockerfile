FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -m threadopt && chown threadopt /app
USER threadopt
COPY --from=build /app/target/threadopt-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:ActiveProcessorCount=4","-jar","app.jar"]
