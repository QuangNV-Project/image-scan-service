# Multi-stage build for optimal image size

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom.xml and download dependencies (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

# Install Tesseract OCR + wget + curl
RUN apt-get update && \
    apt-get install -y \
        tesseract-ocr \
        wget \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create directories
WORKDIR /app
RUN mkdir -p /app/tessdata /app/logs && \
    chown -R appuser:appuser /app

# Download tessdata files directly from GitHub (best trained data)
RUN wget -q https://github.com/tesseract-ocr/tessdata_best/raw/main/vie.traineddata -O /app/tessdata/vie.traineddata && \
    wget -q https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata -O /app/tessdata/eng.traineddata && \
    echo "Tessdata downloaded successfully" && \
    ls -lh /app/tessdata/ && \
    chown -R appuser:appuser /app/tessdata

# Copy JAR from build stage
COPY --from=build /build/target/*.jar /app/app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8092/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
