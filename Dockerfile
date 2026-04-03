# Build stage
FROM eclipse-temurin:8-jdk AS build

WORKDIR /build

COPY src/ src/

RUN mkdir -p bin && \
    find src -name "*.java" > sources.txt && \
    javac -encoding ISO-8859-1 -d bin @sources.txt

RUN printf "Main-Class: de.dion.SimpleHttpServerMain\n\n" > manifest.txt && \
    jar cfm SimpleHttpServer.jar manifest.txt -C bin .

# Run stage
FROM eclipse-temurin:8-jre

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/SimpleHttpServer.jar .

RUN mkdir -p DL

EXPOSE 80

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "SimpleHttpServer.jar", "-start"]
