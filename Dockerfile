# stage 1: build (Gradle + JDK)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# GITHUB_ACTOR = username สำหรับ GitHub Packages (ไม่ลับ), token ส่งผ่าน BuildKit secret id=gh_token
ARG GITHUB_ACTOR
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN --mount=type=secret,id=gh_token \
    GITHUB_TOKEN="$(cat /run/secrets/gh_token 2>/dev/null)" ./gradlew --no-daemon dependencies -q || true
COPY src/ src/
RUN --mount=type=secret,id=gh_token \
    GITHUB_TOKEN="$(cat /run/secrets/gh_token 2>/dev/null)" ./gradlew --no-daemon clean bootJar

# stage 2: run (JRE อย่างเดียว เบา ไม่มี toolchain)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
