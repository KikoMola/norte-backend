# 0001. Código en inglés, interfaz y documentación en español

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.1

## Contexto

Norte es un ERP para el mercado español, con un vocabulario fiscal y contable muy específico (base imponible,
recargo de equivalencia, albarán…). Mezclar idiomas en el código produce nombres como `getFacturaById` o
`InvoiceRectificativa`, y cada persona traduce el mismo término de forma distinta.

## Decisión

- **En inglés:** código, nombres de tablas y columnas, API (rutas, propiedades, `operationId`), eventos de dominio,
  permisos (`invoice:issue`) y códigos de error (`company.tax_id.duplicate`).
- **En español:** la interfaz de usuario (vía ngx-translate), la documentación, los ADRs y los mensajes de commit.
- Ningún término en español en el código, salvo **valores de datos**: textos de plantillas, nombres de impuestos o
  formas de pago que se cargan como datos iniciales.
- La traducción de cada término de negocio se fija en [docs/glosario.md](../glosario.md). Ante un término nuevo, se
  añade al glosario **antes** de usarlo en el código.

## Consecuencias

- El código es legible para cualquier contribuidor del proyecto open source.
- Hay una única traducción por concepto, comprobable en revisión de código.
- Coste: algunos términos no tienen equivalente exacto (albarán → *delivery note*); el glosario lo resuelve.

## Alternativas descartadas

- **Todo en español:** choca con frameworks, librerías y convenciones, y aleja a contribuidores no hispanohablantes.
- **Sin glosario:** cada módulo acabaría con su propia traducción del mismo concepto.
