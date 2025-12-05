# Runtime stage only - JAR is pre-built by GitHub Actions
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy pre-built JAR from GitHub Actions build
ARG JAR_FILE=product-api/build/libs/*.jar
COPY ${JAR_FILE} app.jar

# Metadata
LABEL maintainer="c4ang-order-service"
LABEL description="C4ang Order Service - 주문 관리 마이크로서비스"

# Expose application port
EXPOSE 8084

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
