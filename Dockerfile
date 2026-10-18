# Backend (Spring Boot, Java 26). Prod: deploy/compose.prod.yaml yig'adi.
# JWT kalitlari image'ga TUSHMAYDI (-x generateJwtKeys, .dockerignore) — serverda bir marta yasaladi va
# JWT_PRIVATE_KEY / JWT_PUBLIC_KEY (file:...) orqali beriladi (deploy/README.md).
FROM eclipse-temurin:26-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Bog'liqliklar alohida qatlam — kod o'zgarsa qayta yuklanmaydi
RUN ./gradlew --no-daemon -q dependencies > /dev/null || true
COPY src ./src
RUN ./gradlew --no-daemon -q bootJar -x generateJwtKeys \
    && cp build/libs/*-SNAPSHOT.jar /src/app.jar

FROM eclipse-temurin:26-jre
RUN useradd --system --uid 10002 app && mkdir -p /var/lib/agro/files && chown -R app /var/lib/agro
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    FILES_DIR=/var/lib/agro/files \
    TZ=Asia/Tashkent \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tashkent"
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
