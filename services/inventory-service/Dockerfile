
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -B -pl services/inventory-service -am -DskipTests package

RUN jar_file=$(find services/inventory-service/target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1) \
    && test -n "$jar_file" \
    && cp "$jar_file" /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 --no-create-home appuser
COPY --from=build /workspace/app.jar /app/app.jar

USER 10001
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
