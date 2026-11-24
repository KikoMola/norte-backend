# 0003. Formularios con Reactive Forms tipados

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.3

## Contexto

Casi todas las pantallas del ERP son formularios, algunos grandes (editor de factura con líneas). La utilidad que
pinta en los campos los errores `422` del servidor (`errors[].field` → control) depende de la API de formularios, así
que hay que decidir antes de la primera pantalla.

En Angular 22, **Signal Forms** (`@angular/forms/signals`) es API pública desde la versión 22.0: ya no es
experimental.

## Decisión

**Reactive Forms tipados** (`FormGroup<…>`, `NonNullableFormBuilder`) en todo el frontend.

- Una única utilidad en `core/` aplica los `errors[]` de un Problem Details `422` a los controles por su ruta
  (`taxId`, `lines.2.quantity`).
- Los componentes de `ui/` implementan `ControlValueAccessor`, de modo que sirven con Reactive Forms y, vía la
  interoperabilidad que ofrece Angular, con Signal Forms si algún día se cambia.

## Consecuencias

- API madura, muy documentada y con la que encajan Angular Primitives y el resto del ecosistema.
- Más código que Signal Forms para estados derivados; se compensa con `toSignal()` donde haga falta.
- **Revisión:** antes del editor de factura (paso 1.19) se vuelve a evaluar Signal Forms, que para entonces llevará
  más tiempo como API estable. Si se adopta, se hace en un ADR nuevo y solo para formularios nuevos.

## Alternativas descartadas

- **Signal Forms ya:** encaja mejor con una aplicación zoneless y basada en signals, pero lleva poco tiempo como API
  estable y el ecosistema (librerías de componentes, ejemplos, utilidades) todavía es corto.
- **Template-driven forms:** sin tipado fuerte ni buen soporte para formularios dinámicos.
