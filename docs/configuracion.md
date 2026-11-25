# Configuración

`app` y `bff` se configuran con variables de entorno. Los valores por defecto sirven para el entorno local de
`compose.yaml`; **en producción hay que dar siempre las contraseñas y secretos**.

## app (API, puerto 8081)

| Variable | Por defecto | Qué es |
| --- | --- | --- |
| `PORT` | `8081` | Puerto HTTP. |
| `DB_URL` | `jdbc:postgresql://localhost:5433/norte` | URL JDBC de PostgreSQL. |
| `DB_APP_USER` | `norte_app` | Usuario con el que conecta la aplicación. No es dueño de las tablas ni tiene `BYPASSRLS`. |
| `DB_APP_PASSWORD` | `norte_app` | Contraseña de `DB_APP_USER`. |
| `DB_OWNER_USER` | `norte_owner` | Usuario dueño de esquemas y tablas, solo para las migraciones de Flyway. |
| `DB_OWNER_PASSWORD` | `norte_owner` | Contraseña de `DB_OWNER_USER`. |
| `OIDC_ISSUER_URI` | `http://localhost:8180/realms/norte` | Emisor de los JWT (realm de Keycloak). Debe coincidir exactamente con el `iss` de los tokens. |

| `KEYCLOAK_URL` | `http://localhost:8180` | URL base de Keycloak para su API de administración. Solo la usa el comando `provision-tenant`. |
| `KEYCLOAK_PROVISIONER_SECRET` | `norte-provisioner-dev-secret` | Secreto del cliente de servicio `norte-provisioner`. **Solo desarrollo**; en producción, obligatorio. |
| `FILES_ENDPOINT` | `http://localhost:8333` | API S3 del almacén de ficheros (SeaweedFS), vista desde la aplicación. |
| `FILES_PUBLIC_ENDPOINT` | (el mismo que `FILES_ENDPOINT`) | Dirección con la que el navegador abre las URLs firmadas. En producción, `https://${FILES_DOMAIN}`. |
| `FILES_BUCKET` | `norte` | Bucket de los ficheros. Se crea con la primera escritura. Dentro, cada tenant tiene su carpeta `{tenantId}/`. |
| `FILES_ACCESS_KEY` | `norte-app` | Usuario S3 de la aplicación. |
| `FILES_SECRET_KEY` | `norte-files-dev-secret` | Su clave. **Solo desarrollo**; en producción, obligatoria.

La audiencia exigida es `norte-api` (fija; la añade el mapper del cliente `norte-bff` en Keycloak).

## bff (Backend for Frontend, puerto 8080)

| Variable | Por defecto | Qué es |
| --- | --- | --- |
| `PORT` | `8080` | Puerto HTTP. |
| `OIDC_ISSUER_URI` | `http://localhost:8180/realms/norte` | Emisor OIDC (realm de Keycloak). |
| `BFF_CLIENT_ID` | `norte-bff` | Cliente confidencial de Keycloak. |
| `BFF_CLIENT_SECRET` | `norte-bff-dev-secret` | Secreto del cliente. **Solo desarrollo**; en producción, obligatorio. |
| `API_URI` | `http://localhost:8081` | URL de `app`, a la que se reenvía `/api/**` con el access token. |
| `SESSION_COOKIE_NAME` | `NORTE_SESSION` | Nombre de la cookie de sesión. En producción, `__Host-NORTE_SESSION`. |
| `SESSION_COOKIE_SECURE` | `false` | `true` en producción (HTTPS). Obligatorio si el nombre empieza por `__Host-`. |
| `POST_LOGIN_REDIRECT` | `/` | A dónde vuelve el navegador tras iniciar sesión. |
| `POST_LOGOUT_REDIRECT` | `{baseUrl}` | A dónde vuelve el navegador tras cerrar sesión en Keycloak. Tiene que estar en los *post logout redirect URIs* del cliente. |

El BFF respeta las cabeceras `X-Forwarded-*` (`server.forward-headers-strategy=framework`), así que detrás de un proxy
con TLS construye bien las URL de redirección. El proxy debe ser el único que puede fijarlas.

## Observabilidad (app y bff)

| Variable | Por defecto | Qué es |
| --- | --- | --- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | Colector OTLP por HTTP (base, sin `/v1/...`). En local, `grafana/otel-lgtm`. Se envían trazas (`/v1/traces`) y métricas (`/v1/metrics`). |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Fracción de peticiones trazadas. En producción con tráfico, por ejemplo `0.1`. |
| `DEPLOYMENT_ENVIRONMENT` | `local` | Atributo `deployment.environment.name` de las trazas y métricas. |
| `LOG_FORMAT` | `ecs` | Formato de los logs por consola: `ecs`, `logstash` o `gelf` (todos JSON). |

Los logs JSON llevan `traceId` y `spanId`, y en `app` también `tenantId` de la petición.

**Logs en Loki:** de momento los logs van solo a la consola. Enviarlos por OTLP requiere el appender de logback de
OpenTelemetry (`opentelemetry-logback-appender-1.0`), que todavía se publica como alfa; queda pendiente. En el
servidor, el colector puede recoger la salida JSON de los contenedores.

### Ver una traza

1. `docker compose up -d`, arranca `app` y `bff`, y haz una petición a través del BFF (por ejemplo, abre el frontend).
2. Grafana en http://localhost:3000 → Explore → Tempo → *Search*, servicio `norte-bff`.
3. La traza muestra el span del BFF y, dentro, el de `norte-app` con sus spans de seguridad y de los módulos de Spring Modulith. Las consultas a PostgreSQL aún no generan spans.

## Base de datos

- Roles creados por `infra/postgres/init/01-roles.sql` en local y en los tests: `norte_owner` (Flyway) y `norte_app`
  (aplicación, `NOSUPERUSER NOBYPASSRLS`).
- Un esquema por módulo (`shared`, `parties`, `catalog`…) creado por Flyway, con privilegios por defecto para
  `norte_app`.
- Las tablas de negocio tienen `tenant_id` como primera columna de cada índice, `ENABLE` y `FORCE ROW LEVEL SECURITY`,
  y una política que compara con `current_setting('app.tenant_id')`. El tenant se fija con
  `set_config('app.tenant_id', ?, true)` al empezar cada transacción (`TenantAwareJpaTransactionManager`).
- Fuera de una petición (tareas programadas), el tenant se fija con `TenantContext.runAs(tenantId, ...)`. Sin tenant,
  las tablas con RLS no devuelven ni aceptan filas.
