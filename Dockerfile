# stage 1: build (Gradle + JDK)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon dependencies -q || true
COPY src/ src/
RUN ./gradlew --no-daemon clean bootJar

# stage 2: run (JRE อย่างเดียว เบา ไม่มี toolchain)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
