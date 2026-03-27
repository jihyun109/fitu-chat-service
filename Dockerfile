FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY . .
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test && \
    cp build/libs/*-SNAPSHOT.jar app.jar || cp build/libs/*.jar app.jar

FROM eclipse-temurin:17-jre-jammy AS final

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Pyroscope Java agent
RUN curl -fsSL https://github.com/grafana/pyroscope-java/releases/latest/download/pyroscope.jar \
    -o /opt/pyroscope.jar

ARG UID=10001
RUN adduser --disabled-password --gecos "" \
    --home "/nonexistent" --shell "/sbin/nologin" \
    --no-create-home --uid "${UID}" appuser

USER appuser
WORKDIR /app
COPY --from=build /build/app.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
