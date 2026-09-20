# ==============================================================
# Stage 1: Build the application with Maven
# ==============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
# Cache dependencies
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================
# Stage 2: Runtime image with yt-dlp + ffmpeg
# ==============================================================
FROM eclipse-temurin:17-jre-jammy

# Install yt-dlp, ffmpeg, and Python (required by yt-dlp)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        ffmpeg \
        && pip3 install --break-system-packages yt-dlp \
        && apt-get clean \
        && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render injects PORT as an env variable
ENV PORT=9090

EXPOSE ${PORT}

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
