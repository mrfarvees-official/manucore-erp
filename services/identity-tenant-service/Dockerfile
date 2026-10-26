FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -B -pl services/identity-tenant-service -am -DskipTests package

# fail the build now if the jar is not a Spring Boot executable jar
RUN jar tf services/identity-tenant-service/target/app.jar | grep -q '^BOOT-INF/' \
    && cp services/identity-tenant-service/target/app.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /workspace/app.jar /app/app.jar

USER 10001
EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]