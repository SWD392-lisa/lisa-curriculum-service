# ─── Build stage ───────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Run stage ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S lisa && adduser -S lisa -G lisa
COPY --from=build /app/target/*.jar app.jar
USER lisa
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
