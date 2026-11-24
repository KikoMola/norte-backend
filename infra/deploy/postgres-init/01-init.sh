#!/bin/sh
# Primer arranque de PostgreSQL en producción: roles y bases de datos (paso 0.14).
# Las contraseñas llegan por variables de entorno; nunca se escriben en el repositorio.
#
#   norte_owner  dueño de las tablas de Norte; lo usa Flyway.
#   norte_app    usuario de la aplicación; ni dueño ni BYPASSRLS, así que RLS le aplica siempre.
#   keycloak     dueño de la base de datos de Keycloak.
set -eu

: "${DB_OWNER_PASSWORD:?}" "${DB_APP_PASSWORD:?}" "${KC_DB_PASSWORD:?}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_password="$DB_OWNER_PASSWORD" \
  -v app_password="$DB_APP_PASSWORD" \
  -v keycloak_password="$KC_DB_PASSWORD" <<'SQL'
CREATE ROLE norte_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE norte_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOBYPASSRLS;
CREATE DATABASE norte OWNER norte_owner;
REVOKE ALL ON DATABASE norte FROM PUBLIC;
GRANT CONNECT ON DATABASE norte TO norte_app;

CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
