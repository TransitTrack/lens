# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --version

COPY src src
COPY extension-api extension-api
COPY examples examples

RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin transittrack
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN mkdir -p /extensions && chown transittrack:transittrack app.jar /extensions
ENV LOADER_PATH=/extensions
USER transittrack

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
