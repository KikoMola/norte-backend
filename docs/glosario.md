# Glosario

Interfaz y documentación en español, código en inglés ([ADR 0001](adr/0001-glosario-e-idioma.md)). Ante un término
nuevo, **se añade aquí antes de usarlo** en el código, la API o los eventos.

## Plataforma

| Español (interfaz) | Inglés (código) | Notas |
| --- | --- | --- |
| Organización, cliente del SaaS | Tenant | Cada empresa que usa Norte. Columna `tenant_id`. |
| Usuario | User | Identidad en Keycloak (`sub`). |
| Rol, permiso | Role, permission | Permisos con forma `resource:action` (`invoice:issue`). |
| Módulo contratado | Enabled module | Paso 1.2. |
| Historial, auditoría | History, audit | `AuditEvent` en el contrato. |
| Ajustes de la empresa | Company settings | Datos del propio tenant, no de sus clientes. |

## Terceros y catálogo

| Español (interfaz) | Inglés (código) | Notas |
| --- | --- | --- |
| Empresa, tercero | Company, party | Módulo `parties`. Cliente o proveedor del tenant. |
| Contacto | Contact | |
| NIF, NIE, CIF | Tax ID | `taxId`. Se normaliza a mayúsculas sin separadores. |
| Razón social | Legal name | En el contrato, `name`. |
| Dirección | Address | |
| Producto, familia, tarifa | Product, product family, price list | |

## CRM

| Español (interfaz) | Inglés (código) |
| --- | --- |
| Oportunidad | Opportunity |
| Embudo, etapa | Pipeline, stage |
| Actividad | Activity |

## Ventas y facturación

| Español (interfaz) | Inglés (código) |
| --- | --- |
| Presupuesto | Quote |
| Pedido de venta | Sales order |
| Albarán | Delivery note |
| Factura | Invoice |
| Factura rectificativa | Corrective invoice |
| Borrador | Draft |
| Emitir | Issue |
| Serie de facturación | Invoice series |
| Base imponible | Taxable base |
| Tipo de IVA | Tax rate |
| Recargo de equivalencia | Equivalence surcharge |
| Retención de IRPF | Withholding tax |
| Forma de pago | Payment terms |
| Vencimiento | Due item |
| Cobro, pago | Payment received, payment made |
| Importe | Amount (`Money`) |

## Tesorería y compras

| Español (interfaz) | Inglés (código) |
| --- | --- |
| Remesa | Direct debit batch |
| Conciliación bancaria | Bank reconciliation |
| Pedido de compra, recepción | Purchase order, goods receipt |
| Factura de proveedor | Supplier invoice |

## Inventario

| Español (interfaz) | Inglés (código) |
| --- | --- |
| Almacén, movimiento, saldo | Warehouse, stock movement, stock balance |
| Reserva | Stock reservation |

## Contabilidad

| Español (interfaz) | Inglés (código) |
| --- | --- |
| Asiento | Journal entry |
| Cuenta, subcuenta | Account, subaccount |
| Debe, haber | Debit, credit |
| Libro diario, libro mayor | Journal, general ledger |
| Sumas y saldos | Trial balance |
| Cierre de periodo, cierre de ejercicio | Period close, year-end close |
| Ejercicio fiscal | Fiscal year |
