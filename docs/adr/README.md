# Registro de decisiones de arquitectura (ADR)

Cada decisión que condiciona varios pasos posteriores se escribe aquí **antes** de programar. Un ADR aceptado no se
edita: si la decisión cambia, se escribe uno nuevo que lo sustituye y se marca el antiguo como «Sustituido por …».

| ADR | Decisión | Paso | Estado |
| --- | --- | --- | --- |
| [0001](0001-glosario-e-idioma.md) | Código en inglés, interfaz y documentación en español | 0.1 | Aceptado |
| [0002](0002-autenticacion-bff.md) | Autenticación con BFF (Spring Cloud Gateway) | 0.2 | Aceptado |
| [0003](0003-formularios.md) | Reactive Forms tipados | 0.3 | Aceptado |
| [0004](0004-i18n.md) | i18n con ngx-translate 18 | 0.4 | Aceptado |
| [0005](0005-generacion-de-codigo.md) | orval en el frontend, openapi-generator en el backend | 0.5 | Aceptado |
| [0006](0006-primitivas-de-ui.md) | Angular Primitives y CDK | 0.6 | Aceptado |
| [0007](0007-despliegue-inicial.md) | Docker Compose en un servidor | 0.7 | Aceptado |
| [0008](0008-alcance-normativo.md) | Alcance normativo abierto hasta el hito 6 | 0.8 | Propuesto |
| [0009](0009-dos-repositorios.md) | Backend y frontend en repositorios separados | 0.9 | Aceptado |

## Plantilla

```markdown
# NNNN. Título

- **Estado:** Propuesto | Aceptado | Sustituido por NNNN
- **Fecha:** AAAA-MM-DD
- **Paso del planning:** X.Y

## Contexto
Qué problema hay y qué fuerzas influyen.

## Decisión
Qué se hace.

## Consecuencias
Qué se gana, qué se pierde y qué pasos dependen de esto.

## Alternativas descartadas
Qué otras opciones había y por qué no.
```
