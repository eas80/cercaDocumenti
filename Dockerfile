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

# Default CORS origin for the deployed frontend. Baked in here (rather than
# relying solely on the platform's dashboard env vars) because it's proven to
# reach the process reliably; still overridable by a real runtime env var of
# the same name if one is actually injected.
ENV DOCUMENTSTORE_CORS_ALLOWED_ORIGINS=https://cercadocumenti.onrender.com

# Fixed accounts, baked in for the same reason as above (Render dashboard env
# vars weren't reaching this service). These are plaintext in a public repo's
# history - fine for trivial/throwaway credentials on a low-stakes personal
# app, NOT fine if this backend ever holds anything sensitive. Overridable by
# a real DOCUMENTSTORE_AUTH_USERS env var if one is actually injected.
ENV DOCUMENTSTORE_AUTH_USERS=simona:simona,antonio:antonio

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
