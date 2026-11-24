# 0005. Generación de código desde el contrato

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.5

## Contexto

El contrato OpenAPI (`api/openapi.yaml` de norte-backend) es la fuente de verdad. Sin generación, backend, frontend
y mocks se desalinean en silencio.

## Decisión

- **Backend:** openapi-generator con el generador `spring` en modo `interfaceOnly`. Se genera en cada build; los
  controladores implementan las interfaces generadas.
- **Frontend:** **orval** genera el cliente de Angular (sobre `HttpClient`) y mocks con **MSW**. Los mismos mocks
  sirven en Vitest, en Storybook y en el servidor de desarrollo (`npm run start:mock`).
- Como los repositorios están separados ([ADR 0009](0009-dos-repositorios.md)), el frontend guarda una copia del
  contrato en su propio `api/openapi.yaml`, anotando el commit de origen, y la actualiza con `npm run api:sync`.
- En CI del backend, **Spectral** valida el contrato contra la guía de estilo y **oasdiff** bloquea cambios
  incompatibles.

## Consecuencias

- Un cambio en el contrato rompe la compilación del lado que no se haya adaptado, en vez de fallar en producción.
- Con mocks generados desde el contrato no hace falta Prism.
- La copia del contrato en el frontend puede quedarse atrás; `api:sync` y la revisión de cada PR lo controlan.

## Alternativas descartadas

- **openapi-generator también en el frontend:** genera clientes más pesados y no produce mocks MSW.
- **Escribir el cliente a mano:** se desalinea con el contrato y duplica tipos.
- **Prism como servidor de mocks:** otro proceso más; MSW cubre el mismo caso dentro del navegador y de los tests.
