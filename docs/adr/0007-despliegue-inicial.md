# 0007. Despliegue inicial con Docker Compose en un servidor

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.7

## Contexto

Lo desarrolla y opera una sola persona. Hay que tener el producto desplegado desde el hito 0, sin el coste de operar
un clúster.

## Decisión

- Un servidor (VPS o servidor en casa) con **Docker Compose**: PostgreSQL, Keycloak, `app`, `bff`, el frontend y
  **Caddy** como proxy con TLS automático. Definido en `infra/deploy/`.
- Imágenes publicadas en GitHub Container Registry desde el pipeline.
- **Copias diarias de PostgreSQL a almacenamiento S3**, con retención y una prueba de restauración documentada.
- Despliegue con `just deploy` (SSH al servidor, `docker compose pull` y `up -d`), también invocable desde un
  workflow manual.
- Kubernetes queda como opción en el hito 9, si la carga o la disponibilidad lo piden.

## Consecuencias

- Un solo servidor es un punto único de fallo; se acepta mientras no haya clientes con SLA.
- La migración a Kubernetes reutiliza las imágenes, la configuración por variables de entorno y los health checks.

## Alternativas descartadas

- **Kubernetes desde el principio:** demasiado coste de operación para una persona.
- **PaaS (Fly, Render…):** cómodo, pero Keycloak y PostgreSQL con RLS y dos usuarios piden más control, y el coste
  crece con cada servicio.
