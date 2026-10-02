# One image: the React app is compiled into the API's static resources, so the whole product is a
# single process on a single port with no API origin to configure and no CORS to get wrong.

FROM node:20-alpine AS ui
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=ui /ui/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
# OCR fallback shells out to tesseract.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
ENV SYNDICATE_UPLOAD_DIR=/data/uploads
RUN mkdir -p /data/uploads && useradd --system --uid 10001 syndicate && chown -R syndicate /data
USER syndicate
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=12 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
