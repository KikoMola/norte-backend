# Copias de PostgreSQL a S3: pg_dump de la imagen oficial de PostgreSQL y aws-cli del repositorio oficial de Alpine.
#
#   docker build -f infra/docker/backup/Dockerfile -t norte-backup infra/docker/backup
#
# `docker run ... norte-backup now` hace una copia inmediata y termina.

ARG POSTGRES_IMAGE=postgres:18.7-alpine3.24@sha256:0115267ab5a509022df9319c77be365cef335adb71556c75f60f5360ee247e71

FROM ${POSTGRES_IMAGE}
LABEL org.opencontainers.image.source="https://github.com/KikoMola/norte-backend" \
      org.opencontainers.image.title="norte-backup"
# gosu solo lo usa el entrypoint de PostgreSQL, que aquí no se ejecuta, y arrastra CVE de su stdlib de Go.
RUN apk upgrade --no-cache \
 && apk add --no-cache aws-cli \
 && rm -f /usr/local/bin/gosu
COPY --chmod=0755 backup.sh /usr/local/bin/norte-backup
USER postgres
HEALTHCHECK NONE
ENTRYPOINT ["/usr/local/bin/norte-backup"]
