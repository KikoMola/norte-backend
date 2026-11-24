# 0009. Backend y frontend en repositorios separados

- **Estado:** Aceptado (sustituye la propuesta de monorepo del paso 0.9 del planning)
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.9

## Contexto

El planning proponía un monorepo con `backend/`, `frontend/`, `api/`, `infra/`, `docs/` y `design/`. Se decidió
separarlos para evitar choques entre herramientas, pipelines y ritmos de cambio de cada lado.

## Decisión

Dos repositorios públicos en GitHub, ambos con rama principal `master`:

- **[norte-backend](https://github.com/KikoMola/norte-backend):** subproyectos Gradle `app/` (monolito modular) y
  `bff/`, el contrato en `api/` (fuente de verdad), `infra/` y `docs/` (ADRs, glosario y catálogo de errores).
- **[norte-frontend](https://github.com/KikoMola/norte-frontend):** la aplicación Angular, una copia del contrato
  anotada con su commit de origen ([ADR 0005](0005-generacion-de-codigo.md)) y `design/` (enlace al lienzo).

## Consecuencias

- Cada repositorio tiene su pipeline, sus dependencias y su Renovate, sin interferencias.
- Un cambio de contrato se hace en dos pasos: primero en el backend (con oasdiff vigilando la compatibilidad) y luego
  `api:sync` en el frontend.
- La documentación transversal (ADRs, glosario) vive en el backend; el frontend la enlaza.

## Alternativas descartadas

- **Monorepo:** cambios atómicos entre contrato, backend y frontend, pero un único pipeline más lento y herramientas
  de Java y Node mezcladas en el mismo árbol.
