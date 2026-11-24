# 0006. Primitivas de UI: Angular Primitives y CDK

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.6

## Contexto

El design system propio (`ui/`) necesita comportamiento accesible (foco, teclado, ARIA, posicionamiento de
popovers) sin imponer estilos, porque el aspecto lo dan los tokens de Tailwind del lienzo.

## Decisión

- **Angular Primitives** (`ng-primitives`) como base de los componentes interactivos: botones, campos, menús,
  diálogos, combobox, pestañas, tooltips, etc. Son headless y accesibles.
- **Angular CDK** solo para lo que Primitives no cubre: drag and drop (kanban del hito 2) y virtual scroll (tabla v3
  del hito 3).
- Cada componente de `ui/` envuelve la primitiva y expone una API propia y estable, para poder cambiar la base sin
  tocar las pantallas.

## Consecuencias

- Accesibilidad resuelta en la base; los componentes solo añaden estilo y API.
- Dependencia de un proyecto comunitario (versión 0.x): las actualizaciones pueden traer cambios incompatibles. La
  capa propia de `ui/` lo amortigua.

## Alternativas descartadas o en observación

- **Angular Aria** (oficial del equipo de Angular): es la alternativa a reevaluar si Angular Primitives se estanca o
  si Angular Aria llega a cubrir el mismo catálogo con más estabilidad.
- **Angular Material:** trae su propio diseño, que habría que deshacer para aplicar el lienzo.
- **Spartan / librerías con estilos incluidos:** mismo problema y más acoplamiento.
