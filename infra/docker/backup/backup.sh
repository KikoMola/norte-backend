#!/bin/sh
# Copia diaria de PostgreSQL a S3 (paso 0.30).
#
# Cada día a BACKUP_TIME (UTC, HH:MM) vuelca los roles y cada base de datos de BACKUP_DATABASES en formato custom y
# los sube a s3://BACKUP_S3_BUCKET/BACKUP_S3_PREFIX/AAAA/MM/DD/. La retención se configura con una regla de ciclo de
# vida del bucket. BACKUP_S3_ENDPOINT permite usar un S3 compatible (MinIO, Scaleway, Hetzner…).
set -eu
set -o pipefail

: "${BACKUP_S3_BUCKET:?}" "${BACKUP_DATABASES:?}"
BACKUP_TIME="${BACKUP_TIME:-03:00}"
BACKUP_S3_PREFIX="${BACKUP_S3_PREFIX:-norte}"

s3_cp() {
  if [ -n "${BACKUP_S3_ENDPOINT:-}" ]; then
    aws s3 cp --only-show-errors --endpoint-url "$BACKUP_S3_ENDPOINT" - "$1"
  else
    aws s3 cp --only-show-errors --sse AES256 - "$1"
  fi
}

backup() {
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  dest="s3://${BACKUP_S3_BUCKET}/${BACKUP_S3_PREFIX}/$(date -u +%Y/%m/%d)"
  echo "backup: inicio ${stamp}"
  pg_dumpall --globals-only --no-role-passwords | s3_cp "${dest}/globals-${stamp}.sql"
  for db in $BACKUP_DATABASES; do
    pg_dump --format=custom --dbname="$db" | s3_cp "${dest}/${db}-${stamp}.dump"
    echo "backup: ${db} subido a ${dest}/${db}-${stamp}.dump"
  done
  echo "backup: fin"
}

seconds_until_next_run() {
  now="$(date -u +%s)"
  target="$(date -u -d "$(date -u +%Y-%m-%d) ${BACKUP_TIME}" +%s)"
  if [ "$target" -le "$now" ]; then
    target=$((target + 86400))
  fi
  echo $((target - now))
}

if [ "${1:-}" = "now" ]; then
  backup
  exit 0
fi

while true; do
  wait_seconds="$(seconds_until_next_run)"
  echo "backup: próxima copia en ${wait_seconds}s (${BACKUP_TIME} UTC)"
  sleep "$wait_seconds"
  backup || echo "backup: ERROR, se reintentará en la próxima ventana" >&2
done
