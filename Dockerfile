# --- build stage ---
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so they're cached in their own layer.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q package -DskipTests

# --- runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --uid 1000 appuser \
    && mkdir -p /data/documents \
    && chown -R appuser:appuser /data /app

COPY --from=build /workspace/target/*.jar app.jar

USER appuser

# Default storage location: if a Render Disk (or any volume) is mounted at
# /data, documents survive restarts/redeploys; otherwise this is just
# ephemeral container storage. Override via DOCUMENTSTORE_STORAGE_DISK_DIRECTORY.
ENV DOCUMENTSTORE_STORAGE_DISK_DIRECTORY=/data/documents

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
