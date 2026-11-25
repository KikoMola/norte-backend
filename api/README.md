# Contrato de la API

`openapi.yaml` es la **fuente de verdad** de la API (contract first):

- El backend genera sus interfaces de Spring con openapi-generator (`spring`, `interfaceOnly`).
- El frontend genera su cliente de Angular y sus mocks MSW con orval, a partir de este mismo fichero.
- Ningún endpoint existe en código sin estar antes aquí.

Validación: `npx @stoplight/spectral-cli lint api/openapi.yaml` (reglas en `.spectral.yaml`). En CI además se
compara con la versión de `master` mediante oasdiff para detectar cambios incompatibles.

## Guía de estilo

### Recursos y rutas

- Recursos **en inglés y en plural**, en kebab-case: `/companies`, `/invoice-series`.
- Identificadores en la ruta: `/companies/{companyId}`. Siempre UUID.
- Acciones que no son CRUD, como subrecurso verbal en POST: `POST /invoices/{invoiceId}/issue`.
- `operationId` en camelCase con verbo delante: `listCompanies`, `getCompany`, `createCompany`, `issueInvoice`.
- Propiedades JSON en camelCase.

### Versionado

- Versión mayor en la URL: `/api/v1`. Solo se crea `/api/v2` ante un cambio incompatible que no se pueda evitar.
- Añadir campos opcionales o endpoints no es un cambio incompatible. Quitar o renombrar, sí (oasdiff lo bloquea).

### Paginación

- **Por cursor**, nunca por número de página: `?cursor=…&limit=25`.
- `limit` entre 1 y 100, 25 por defecto.
- Respuesta: `{ "items": [...], "pageInfo": { "nextCursor": "…" | null, "hasNext": true | false } }`.
- El cursor es opaco para el cliente.

### Filtros y ordenación

- Filtros como query params con el nombre del campo: `?status=issued&customerId=…`. Búsqueda libre con `q`.
- Rangos con sufijos `From`/`To`: `?issueDateFrom=2026-01-01&issueDateTo=2026-03-31`.
- Ordenación con `sort` y `-` para descendente: `sort=-issueDate`. Los valores admitidos se enumeran en el contrato.

### Tipos

- Fechas en ISO 8601: `date` para días (`2026-11-24`), `date-time` en UTC para instantes (`2026-11-24T09:30:00Z`).
- **Importes como string decimal con su moneda**, con el esquema `Money`: `{ "amount": "1210.00", "currency": "EUR" }`.
  Nunca como número en coma flotante.
- Países en ISO 3166-1 alfa-2 y monedas en ISO 4217.

### Errores

- **Siempre Problem Details** (RFC 9457) con `application/problem+json` y el esquema `Problem`.
- `code` es estable y sirve de clave de traducción en el frontend (`company.tax_id.duplicate`). Catálogo en
  `docs/errores.md`.
- `traceId` en todos los errores, para encontrar la traza en Tempo.
- Validación de reglas de negocio: `422` con la lista `errors[]` (`field`, `code`, `message`).
- JSON mal formado o parámetros con tipo incorrecto: `400`.
- Recurso de otro tenant: `404`, nunca `403`, para no revelar que existe.

### Concurrencia e idempotencia

- Los recursos modificables devuelven `ETag`; las actualizaciones exigen `If-Match` y responden `412` si no coincide.
- Los POST con efectos importantes aceptan `Idempotency-Key` (UUID). Repetir la clave devuelve la misma respuesta,
  con la cabecera `Idempotency-Replayed: true`, sin repetir el efecto. Las respuestas 5xx no se guardan (se puede
  reintentar con la misma clave), la misma clave con otra petición es un 422 `idempotency.key_reused` y las claves
  caducan a las 24 horas. Son por tenant.

### Seguridad

- La API valida un JWT de Keycloak (`bearerAuth`, audiencia `norte-api`). El tenant sale siempre del token, **nunca**
  de un parámetro ni del cuerpo.
- El navegador no ve tokens: llama a través del BFF con su cookie de sesión.
