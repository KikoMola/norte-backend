# 0008. Alcance normativo: decisiones abiertas hasta el hito 6

- **Estado:** Propuesto
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.8

## Contexto

Un programa de facturación vendido en España tiene que cumplir el reglamento de sistemas informáticos de
facturación (RD 1007/2023, «Verifactu») y, para la factura electrónica entre empresas, el desarrollo de la Ley
Crea y Crece (RD 238/2026). Los plazos de obligación se han aplazado varias veces, así que las fechas exactas se
verifican en el BOE al empezar el hito 6, no ahora.

Lo que sí condiciona el diseño desde el hito 1 ya está decidido en el paso 1.13: facturas emitidas inmutables,
rectificativas en lugar de ediciones, registro de eventos de cada factura, totales por tipo de IVA guardados como
dato y sitio reservado para la huella y el estado de envío.

## Decisiones que quedan abiertas (se cierran en el hito 6)

1. **Modo Verifactu o no Verifactu.** Verifactu envía cada registro a la AEAT en el momento; el modo no Verifactu
   exige garantías de integridad e inalterabilidad en local (encadenamiento, firma, registro de eventos). Opciones:
   ofrecer solo Verifactu (más simple), solo no Verifactu, o los dos como opción por tenant.
2. **Territorios forales.** Si se cubre **TicketBAI** (País Vasco: Álava, Bizkaia y Gipuzkoa, cada una con sus
   particularidades) y el sistema de **Navarra**, o si Norte se limita al territorio común en su primera versión
   vendible.
3. **Empresas acogidas al SII.** Quedan fuera de Verifactu; hay que decidir si Norte las soporta (envío de libros al
   SII) o las deja fuera del público objetivo al principio.
4. **Factura electrónica B2B.** Formatos (UBL, Facturae, CII), conexión con la solución pública de la AEAT o con
   plataformas privadas, y estados de la factura.

## Consecuencias

- El modelo de factura del hito 1 no puede depender de ninguna de estas opciones.
- Antes de vender el producto hay que haber cerrado este ADR y, si aplica, presentado la declaración responsable del
  productor del software.

## Fuentes a revisar en el hito 6

Las del apartado «Fuentes de la sección normativa» del planning, más el BOE vigente en ese momento.
