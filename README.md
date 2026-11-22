# Norte · backend

CRM + ERP open source para pymes españolas. Este repositorio contiene el backend; el frontend (Angular) vive en
[norte-frontend](https://github.com/KikoMola/norte-frontend).

## Arquitectura

```
navegador ──cookie HttpOnly──▶ bff (8080) ──Bearer JWT──▶ app (8081) ──▶ PostgreSQL
                                  │
                                  └──OIDC──▶ Keycloak (8180)
```

| Subproyecto | Qué es |
| --- | --- |
| `app/` | Monolito modular (Java 25, Spring Boot 4.2, Spring Modulith). Resource server: valida el JWT de Keycloak. |
| `bff/` | Backend for Frontend (Spring Cloud Gateway Server MVC). Hace el login OIDC, guarda los tokens en la sesión del servidor y reenvía `/api/**` a `app` con el access token. El navegador nunca ve un token. |
| `infra/` | Realm de Keycloak y scripts de PostgreSQL para el entorno local. |

### Cómo funciona la sesión

- `GET /oauth2/authorization/keycloak` inicia el login (authorization code + PKCE).
- La sesión va en la cookie `NORTE_SESSION` (`HttpOnly`, `SameSite=Lax`).
- CSRF: el BFF emite la cookie `XSRF-TOKEN` y espera la cabecera `X-XSRF-TOKEN` en las peticiones que modifican datos
  (Angular `HttpClient` lo hace solo).
- `/api/**` sin sesión responde `401` en vez de redirigir.
- `POST /logout` responde `202` con la URL de cierre de sesión de Keycloak en `Location`; la SPA navega a ella.

## Requisitos

- Docker
- JDK 17 o superior para lanzar Gradle. El JDK 25 del proyecto lo descarga Gradle automáticamente si no lo tienes.

## Arrancar en local

```bash
docker compose up -d          # PostgreSQL, Keycloak, Grafana (otel-lgtm) y Mailpit
./gradlew :app:bootRun        # API en http://localhost:8081
./gradlew :bff:bootRun        # BFF en http://localhost:8080
```

Después arranca el frontend (`npm start` en norte-frontend) y entra en http://localhost:4200.

| Servicio | URL | Credenciales |
| --- | --- | --- |
| Keycloak | http://localhost:8180 | `admin` / `admin` |
| Grafana | http://localhost:3000 | — |
| Mailpit | http://localhost:8025 | — |
| PostgreSQL | `localhost:5433/norte` | `norte_owner` (Flyway) y `norte_app` (aplicación) |

Usuarios de prueba del realm `norte` (contraseña = usuario):

| Usuario | Tenant |
| --- | --- |
| `ana` | `11111111-1111-1111-1111-111111111111` |
| `bruno` | `22222222-2222-2222-2222-222222222222` |

> Todas las contraseñas y el secreto del cliente `norte-bff` de este repositorio son **solo para desarrollo**. En
> producción llegan por variables de entorno (`BFF_CLIENT_SECRET`, `DB_APP_PASSWORD`, `DB_OWNER_PASSWORD`…).

## Tests

```bash
./gradlew build
```

Los tests de integración usan Testcontainers, así que necesitan Docker.
