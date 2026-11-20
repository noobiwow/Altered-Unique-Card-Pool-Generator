# syntax=docker/dockerfile:1.7
#
# One container, one process: the Spring Boot jar serves the API and the built
# Vue app. Build stages run on the build machine's own architecture (no
# emulation on Apple Silicon); dependency downloads are cached between builds.

# ---- Frontend ---------------------------------------------------------------
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend
WORKDIR /build/frontend_vue
COPY frontend_vue/package.json frontend_vue/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend_vue/ ./
RUN npm run build

# ---- Backend ----------------------------------------------------------------
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend_vue/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination /build/app

# ---- Runtime ----------------------------------------------------------------
# Java 21 runtime (runs the Java 17 bytecode): the only Temurin Alpine JRE published for amd64 and arm64.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/data /app/cardsdata && chown -R app:app /app
WORKDIR /app
# Layers from the least to the most frequently changed: code changes only rebuild the last one.
COPY --from=backend /build/app/dependencies/ ./
COPY --from=backend /build/app/spring-boot-loader/ ./
COPY --from=backend /build/app/snapshot-dependencies/ ./
COPY --from=backend /build/app/application/ ./
USER app
ENV CARDPOOL_DATA_DIR=/app/data \
    CARDPOOL_CARDSDATA_DIR=/app/cardsdata
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
  CMD wget -qO- http://localhost:8080/api/form/formValues >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
