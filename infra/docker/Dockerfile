# Imagen de un subproyecto Spring Boot (app o bff). Se construye desde la raíz del repo:
#
#   docker build -f infra/docker/Dockerfile --build-arg MODULE=app -t norte-app .
#   docker build -f infra/docker/Dockerfile --build-arg MODULE=bff --build-arg PORT=8080 -t norte-bff .

ARG JDK_IMAGE=eclipse-temurin:25.0.5.1_2-jdk-noble@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125
ARG JRE_IMAGE=eclipse-temurin:25.0.5.1_2-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89

# --- build: compila el bootJar y lo separa en capas de Spring Boot ---
FROM ${JDK_IMAGE} AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    test -n "${MODULE}" \
 && ./gradlew --no-daemon --console=plain ":${MODULE}:bootJar" -x test \
 && jar="$(find "${MODULE}/build/libs" -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
 && java -Djarmode=tools -jar "${jar}" extract --layers --launcher --destination /extracted

# --- runtime: solo JRE, usuario sin privilegios ---
FROM ${JRE_IMAGE}
ARG MODULE
ARG PORT=8081
LABEL org.opencontainers.image.source="https://github.com/KikoMola/norte-backend" \
      org.opencontainers.image.title="norte-${MODULE}"
RUN addgroup -S norte && adduser -S -G norte -H -h /app norte
WORKDIR /app
COPY --from=build --chown=norte:norte /extracted/dependencies/ ./
COPY --from=build --chown=norte:norte /extracted/spring-boot-loader/ ./
COPY --from=build --chown=norte:norte /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=norte:norte /extracted/application/ ./
USER norte
ENV PORT=${PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE ${PORT}
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
