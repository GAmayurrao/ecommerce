# ─────────────────────────────────────────────────────────────
# Stage 1: BUILD
#   Uses a full Maven + JDK 21 image to compile & package the JAR
# ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first — lets Docker cache the dependency-download
# layer. Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code and build the fat JAR (skip tests — CI handles those)
COPY src ./src
RUN mvn clean package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────────────────────
# Stage 2: RUN
#   Lightweight Alpine JRE — no compiler, no Maven, much smaller image
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security best-practice: run as a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Set ownership so the non-root user can read the JAR
RUN chown appuser:appgroup app.jar

USER appuser

# Application port
EXPOSE 8080

# Health check — Docker / orchestrators use this to know when the app is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

