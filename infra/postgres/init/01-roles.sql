-- Roles de base de datos (paso 0.14 del planning).
-- Solo para desarrollo y tests: en producción las contraseñas llegan por variables de entorno.
--
--  norte_owner  dueño de los esquemas y tablas; lo usa Flyway.
--  norte_app    usuario de la aplicación; no es dueño y no tiene BYPASSRLS, así que RLS le aplica siempre.

CREATE ROLE norte_owner LOGIN PASSWORD 'norte_owner';
CREATE ROLE norte_app LOGIN PASSWORD 'norte_app' NOSUPERUSER NOBYPASSRLS;

ALTER DATABASE norte OWNER TO norte_owner;
REVOKE ALL ON DATABASE norte FROM PUBLIC;
GRANT CONNECT ON DATABASE norte TO norte_app;
