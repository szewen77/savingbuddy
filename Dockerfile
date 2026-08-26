# Render has no native Java runtime, so a Docker image is the only path for a
# Spring Boot service. Multi-stage: the build stage needs a JDK, Maven and Node
# (the pom builds the React app into the JAR); the runtime stage needs none of it.

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy only what the dependency resolution needs first, so a code-only change
# reuses the cached dependency layer instead of re-downloading Maven and Node.
COPY backend/pom.xml backend/pom.xml
COPY backend/mvnw backend/mvnw
COPY backend/.mvn backend/.mvn
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd backend && ./mvnw -B -q dependency:go-offline -DskipFrontend

COPY backend backend
COPY frontend frontend
# Tests run in CI, not here: the image build should not depend on a database.
RUN cd backend && ./mvnw -B -q clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Never run as root. The base image does not define a non-root user, so make one.
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY --from=build --chown=app:app /src/backend/target/*.jar /app/app.jar
USER app

# Render's free instance has 512MB. MaxRAMPercentage lets the JVM size its heap
# from the container limit rather than the host's memory, which is what makes a
# container OOM instead of GC.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# Documentation only — the platform sets PORT and the app reads it.
EXPOSE 8080

# exec form via sh so JAVA_OPTS expands, while still receiving signals as PID 1.
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
