# Cómo contribuir

¡Gracias por querer ayudar con Norte! Estas son las normas del repositorio.

## Idioma

- **Código, API, eventos y permisos en inglés.** Interfaz y documentación en español.
- Ante un término de negocio nuevo, añádelo antes a `docs/glosario.md`.

## Ramas

- La rama principal es **`master`** y siempre debe estar en verde.
- Para contribuciones externas, trabaja en una rama con el formato `tipo/paso-descripcion`, por ejemplo
  `feat/1.14-numeracion-facturas` o `fix/companies-nif-duplicado`.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/) **en español**, citando el número de paso del
planning cuando lo haya:

```
feat(invoicing): 1.14 numeración por serie y ejercicio sin huecos
fix(parties): 1.10 normaliza el NIF antes de buscar duplicados
ci: 0.12 análisis de imágenes con Trivy
```

- Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`.
- Ámbito: el módulo (`parties`, `invoicing`, `bff`…) o la parte afectada.
- Commits **pequeños** y con un único propósito; mejor varios que uno grande.

## Pull requests

- Toda contribución externa entra por pull request contra `master` y necesita revisión.
- El pipeline de CI tiene que pasar: build, tests, contrato (Spectral y oasdiff), Gitleaks, Trivy y SonarQube Cloud.
- Si cambias la API, cambia primero `api/openapi.yaml` (contract first) siguiendo `api/README.md`.
- Las decisiones de arquitectura nuevas van en un ADR en `docs/adr/`.

## Definición de hecho

Un cambio de funcionalidad está terminado cuando:

- Tiene su migración Flyway, si toca base de datos.
- Las reglas de dominio tienen tests unitarios.
- Tiene tests de integración con Testcontainers, incluido uno que demuestre que otro tenant no ve los datos.
- El endpoint está en el contrato y pasa Spectral.
- Los permisos se comprueban en el backend.
- El pipeline está en verde.

## Seguridad

No abras issues públicos para vulnerabilidades: usa
[Report a vulnerability](https://github.com/KikoMola/norte-backend/security/advisories/new) en la pestaña Security.
