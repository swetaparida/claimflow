# syntax=docker/dockerfile:1.7

# ---- build stage -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/claimflow.jar extract --layers --launcher --destination target/extracted

# ---- runtime stage -----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S claimflow && adduser -S claimflow -G claimflow
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER claimflow
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
