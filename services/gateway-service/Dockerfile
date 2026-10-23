FROM maven:3.9-eclipse-temurin-25 as build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --no-create-home appuser
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
USER 10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exce java $JAVA_OPTS -jar app.jar"]