# Norte · backend

[![CI](https://github.com/KikoMola/norte-backend/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/KikoMola/norte-backend/actions/workflows/ci.yml)

CRM + ERP open source para pymes españolas. Este repositorio contiene el backend; el frontend (Angular) vive en
[norte-frontend](https://github.com/KikoMola/norte-frontend).

## Arquitectura

```
navegador ──cookie HttpOnly──▶ bff (8080) ──Bearer JWT──▶ app (8081) ──▶ PostgreSQL
                                  │
                                  └──OIDC──▶ Keycloak (8180)
```

| Carpeta | Qué es |
| --- | --- |
| `app/` | Monolito modular (Java 25, Spring Boot 4.2, Spring Modulith). Resource server: valida el JWT de Keycloak. |
| `bff/` | Backend for Frontend (Spring Cloud Gateway Server MVC). Hace el login OIDC, guarda los tokens en la sesión del servidor y reenvía `/api/**` a `app` con el access token. El navegador nunca ve un token. |
| `api/` | Contrato OpenAPI, fuente de verdad de la API, y su guía de estilo. |
| `docs/` | ADRs, glosario y catálogo de errores. |
| `infra/` | Realm de Keycloak y PostgreSQL para local (`keycloak/`, `postgres/`), imágenes (`docker/`) y producción (`deploy/`). |

### Cómo funciona la sesión

- `GET /oauth2/authorization/keycloak` inicia el login (authorization code + PKCE).
- La sesión va en la cookie `NORTE_SESSION` (`HttpOnly`, `SameSite=Lax`; `__Host-NORTE_SESSION` y `Secure` en
  producción).
- CSRF: el BFF emite la cookie `XSRF-TOKEN` y espera la cabecera `X-XSRF-TOKEN` en las peticiones que modifican datos
  (Angular `HttpClient` lo hace solo).
- `/api/**` sin sesión responde `401` en vez de redirigir.
- `POST /logout` responde `202` con la URL de cierre de sesión de Keycloak en `Location`; la SPA navega a ella.

## Requisitos

- Docker
- JDK 17 o superior para lanzar Gradle. El JDK del proyecto (Temurin 25, fijado en `.sdkmanrc`) lo descarga Gradle
  automáticamente si no lo tienes.
- [just](https://github.com/casey/just) para las tareas habituales (opcional: cada receta es un comando normal).
- Node 24 (`.nvmrc`), solo para validar el contrato con Spectral.

## Tareas habituales

| Comando | Qué hace |
| --- | --- |
| `just up` / `just down` | Levanta o para el entorno local: PostgreSQL, Keycloak, Grafana (otel-lgtm), Mailpit y SeaweedFS |
| `just run-app` | API en http://localhost:8081 |
| `just run-bff` | BFF en http://localhost:8080 |
| `just test` | Build y todos los tests (Testcontainers necesita Docker) |
| `just api` | Valida `api/openapi.yaml` con Spectral y regenera el código del contrato |
| `just reset-db` | Borra la base de datos local y la recrea vacía (pide confirmación) |
| `just provision-dev` | Da de alta en la base de datos los tenants de `ana` y `bruno` (después de `just up` y de un `reset-db`) |
| `just provision-tenant …` | Alta de un tenant nuevo: organización y administrador en Keycloak, y datos iniciales (ver [Alta de tenants](#alta-de-tenants)) |
| `just deploy` | Despliega en el servidor por SSH (ver [Despliegue](#despliegue)) |

Con el backend en marcha, arranca el frontend (`npm start` en norte-frontend) y entra en http://localhost:4200.

| Servicio | URL | Credenciales |
| --- | --- | --- |
| Keycloak | http://localhost:8180 | `admin` / `admin` |
| Grafana | http://localhost:3000 | — |
| Mailpit | http://localhost:8025 | — |
| SeaweedFS (API S3 de ficheros) | http://localhost:8333 | `norte-app` / `norte-files-dev-secret` |
| PostgreSQL | `localhost:5433/norte` | `norte_owner` (Flyway) y `norte_app` (aplicación) |

PostgreSQL va en el puerto **5433** para no chocar con un PostgreSQL instalado en la máquina.

Usuarios de prueba del realm `norte` (contraseña = usuario):

| Usuario | Tenant |
| --- | --- |
| `ana` | `11111111-1111-1111-1111-111111111111` |
| `bruno` | `22222222-2222-2222-2222-222222222222` |

> Todas las contraseñas y los secretos de los clientes `norte-bff` y `norte-provisioner` de este repositorio son
> **solo para desarrollo**. En producción llegan por variables de entorno.

## Alta de tenants

Cada empresa que usa Norte es un **tenant**. Darlo de alta es un comando, no un endpoint: lo lanza quien administra el
servidor (paso 1.1 del planning).

```sh
just provision-tenant --name=Globex --admin-username=gloria --admin-email=gloria@globex.test     --admin-first-name=Gloria --admin-last-name=Gómez --modules=crm,invoicing
```

1. En Keycloak crea una **organización** con el atributo `tenant_id` y su primer usuario administrador (roles `admin`
   y `user`), con una contraseña temporal que el comando imprime y que hay que cambiar en el primer acceso.
2. En PostgreSQL crea el tenant, activa los **módulos contratados** (`--modules`; por defecto `crm,invoicing`), da los
   permisos por defecto a cada rol y carga los datos iniciales de cada módulo.

Es **idempotente**: repetirlo con los mismos datos no crea nada nuevo, solo completa lo que falte (módulos o
permisos nuevos). Sin `--tenant-id` reutiliza el tenant de la organización con ese nombre, o genera uno.

En producción se lanza con la misma imagen:
`docker compose -f compose.prod.yaml --env-file .env run --rm app provision-tenant --name=… …`.

**Módulos y permisos.** Lo común (empresas, catálogo, ajustes) siempre está; el resto se contrata por tenant y un
endpoint de un módulo no contratado responde 403 `module.disabled`. Cada endpoint exige un permiso
(`invoice:issue`, `company:edit`…); los roles del token se traducen a permisos con la tabla `shared.role_permission`
de cada tenant. `GET /me` devuelve los permisos y módulos efectivos para que el frontend oculte lo que no se puede
hacer.

## Integración continua

`.github/workflows/ci.yml` se ejecuta en cada push a `master` y en cada pull request:

- **Build y tests** con Gradle y JDK 25, y análisis de las dependencias de los jars con Trivy.
- **Contrato:** Spectral (`.spectral.yaml`) y oasdiff, que bloquea cambios incompatibles respecto a la versión
  anterior.
- **Seguridad:** Gitleaks sobre todo el historial y Trivy sobre el repositorio (dependencias, secretos y configuración).
- **SonarQube Cloud** con quality gate, si existe el secret `SONAR_TOKEN`.
- **Imágenes** (solo en `master`): `ghcr.io/kikomola/norte-app`, `norte-bff` y `norte-backup`, con tags `latest` y
  `sha-<commit>`, analizadas con Trivy antes de publicarse.

Las actions van fijadas por SHA y las herramientas (Trivy, Gitleaks, oasdiff) por digest de su imagen oficial.
Renovate (`renovate.json`) propone las actualizaciones.

## Despliegue

Producción es un único servidor con Docker Compose (`infra/deploy/`):

- **Caddy** con TLS automático: `https://NORTE_DOMAIN` sirve la SPA y manda `/api`, `/oauth2`, `/login/oauth2` y
  `/logout` al BFF.
- **Keycloak** en su propio subdominio (`https://AUTH_DOMAIN`), con la consola de administración sin publicar.
- **Ficheros** en SeaweedFS, compatible con S3, con una carpeta por tenant. El navegador descarga con URLs firmadas
  de 5 minutos desde `https://FILES_DOMAIN`, que solo admite lecturas. Se usa SeaweedFS porque MinIO comunitario
  está archivado y ya no publica imágenes.
- PostgreSQL con roles separados para Flyway y la aplicación, y una base de datos aparte para Keycloak.
- **Copias diarias** de PostgreSQL a S3, o a un S3 compatible, con el servicio `backup`.
- Grafana solo en `127.0.0.1:3000` del servidor (accede con `ssh -L 3000:localhost:3000`).

Primer despliegue:

1. Servidor con Docker y un usuario con acceso a Docker. DNS de `NORTE_DOMAIN`, `AUTH_DOMAIN` y `FILES_DOMAIN` apuntando a él, y
   puertos 80 y 443 abiertos.
2. En el servidor, crea `/opt/norte/.env` a partir de `infra/deploy/.env.example`.
3. Desde tu máquina: `DEPLOY_HOST=deploy@tu-servidor just deploy`, o el workflow **Deploy** (manual) con los secrets
   `DEPLOY_HOST`, `DEPLOY_SSH_KEY` y `DEPLOY_KNOWN_HOSTS` en el entorno `production`.
4. Crea el realm `norte` en Keycloak, por túnel SSH a la consola de administración, a partir de
   `infra/keycloak/realm-norte.json` **sin** los usuarios de prueba, con un secreto nuevo para `norte-bff` (el
   mismo que `BFF_CLIENT_SECRET`) y otro para `norte-provisioner` (el mismo que `KEYCLOAK_PROVISIONER_SECRET`), y con
   las URL de redirección de tu dominio.
5. Da de alta el primer tenant con `provision-tenant` (ver [Alta de tenants](#alta-de-tenants)).

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

[Apache 2.0](LICENSE): puedes usar, copiar, modificar, redistribuir y vender este código, también en productos
cerrados, siempre que conserves el aviso de copyright y la licencia.
