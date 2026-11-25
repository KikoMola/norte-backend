# Catálogo de errores

Todos los errores de la API son **Problem Details** (RFC 9457) con `Content-Type: application/problem+json` y el
esquema `Problem` de `api/openapi.yaml`:

```json
{
  "type": "https://norte.dev/problems/company-tax-id-duplicate",
  "title": "Ya existe una empresa con ese NIF",
  "status": 409,
  "code": "company.tax_id.duplicate",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

- `code` es **estable**: el frontend lo usa como clave de traducción y para decidir qué hacer. No se cambia nunca; si
  cambia el significado, se crea un código nuevo.
- `type` se deriva de `code` (puntos y guiones bajos pasan a guiones) bajo `https://norte.dev/problems/`.
- `traceId` permite encontrar la traza en Tempo (Grafana → Explore → Tempo → buscar por Trace ID).
- `title` y `message` están en español y son solo un texto por defecto.

Implementación: `com.norte.shared.api.problem.ProblemAdvice` (excepciones de la aplicación) y
`ProblemSecurityHandlers` / `TenantFilter` (errores de seguridad, antes de llegar a los controladores).

## Códigos generales

| Código | Estado | Cuándo |
| --- | --- | --- |
| `auth.unauthenticated` | 401 | No hay token, o el token no es válido: firma, emisor, caducidad o audiencia distinta de `norte-api`. |
| `auth.tenant_missing` | 401 | El token es válido pero no lleva un claim `tenant_id` con un UUID. |
| `auth.forbidden` | 403 | El usuario no tiene el permiso que exige el endpoint (`@RequiresPermission`). |
| `module.disabled` | 403 | El endpoint es de un módulo (`crm`, `invoicing`…) que el tenant no tiene contratado. |
| `validation` | 422 | Reglas de validación incumplidas. El detalle va en `errors[]`. |
| `request.malformed` | 400 | El cuerpo no es un JSON válido o no encaja con el esquema. |
| `request.invalid_parameter` | 400 | Un parámetro de consulta, cabecera o cursor no es válido (`limit` fuera de rango, `sort` no admitido, cursor manipulado o de otra ordenación). |
| `resource.not_found` | 404 | La ruta no existe. |
| `request.method_not_allowed` | 405 | Método HTTP no admitido en la ruta. |
| `request.not_acceptable` | 406 | No se puede responder en el formato pedido en `Accept`. |
| `request.unsupported_media_type` | 415 | `Content-Type` no admitido. |
| `request.invalid` | 4xx | Otro error del cliente detectado por Spring MVC. |
| `concurrency.conflict` | 409 | Otro usuario modificó el registro a la vez (bloqueo optimista con `@Version`). |
| `concurrency.precondition_failed` | 412 | El `If-Match` falta o no coincide con la versión actual: el recurso cambió desde que se leyó. Se compara dentro de la transacción que modifica (`StaleVersionException`). |
| `request.too_large` | 413 | El fichero subido pasa de 1 MB, el límite de cualquier subida. |
| `idempotency.key_reused` | 422 | La `Idempotency-Key` ya se usó con otra petición (otra ruta u otro cuerpo). |
| `idempotency.in_progress` | 409 | La petición original con esa `Idempotency-Key` aún se está procesando. |
| `internal` | 500 | Error inesperado. Se registra en el log con su `traceId`. |

## Códigos de `errors[]` (validación)

Cada elemento de `errors[]` tiene `field` (ruta del campo: `taxId`, `lines[2].quantity`), `code` y `message`.

| Código | Significado |
| --- | --- |
| `required` | Campo obligatorio ausente o vacío. |
| `length` | Longitud fuera de los límites del contrato. |
| `pattern` | No cumple el formato. |
| `email` | No es un email válido. |
| `min` / `max` | Valor numérico fuera de rango. |
| `invalid` | Otro error de formato. |
| `tax_id.invalid` | El NIF, NIE o CIF no es válido (formato o dígito de control). |
| `too_long` | Texto más largo de lo permitido. |
| `email.invalid` | El correo no es válido. |
| `phone.invalid` | El teléfono no es válido (cifras, espacios, paréntesis, guiones y `+` inicial; de 6 a 30). |
| `website.invalid` | La web no es válida. |
| `country.invalid` | El país no es un código ISO 3166-1 alfa-2. |
| `postal_code.invalid` | El código postal español no tiene 5 cifras o no empieza por 01 a 52. |
| `month.invalid` | El mes no va de 1 a 12. |
| `time_zone.invalid` | No es una zona horaria IANA de región (`Europe/Madrid`). |
| `language.unsupported` | El idioma no está disponible (`es`, `en`). |
| `logo.unsupported_type` | El logo no es PNG, JPEG ni WebP. Se mira el contenido, no la extensión. |
| `logo.too_large` | El logo pasa de 512 KB. |
| `amount.invalid` | El importe tiene más de 4 decimales o más de 15 cifras enteras. |
| `currency.unsupported` | La moneda no se admite en ese campo (el límite de crédito va en euros). |
| `company.kind.required` | La empresa no es ni cliente ni proveedor; tiene que ser al menos una cosa. |
| `contact.role.invalid` | La función del contacto no es una de las del contrato. |
| `contact.limit` | La empresa ya tiene 200 contactos. |

## Códigos por módulo

### shared

| Código | Estado | Cuándo |
| --- | --- | --- |
| `settings.not_found` | 404 | El tenant se dio de alta antes del paso 1.9 y no tiene ajustes: repetir `provision-tenant` los crea. |

### parties

| Código | Estado | Cuándo |
| --- | --- | --- |
| `company.not_found` | 404 | La empresa no existe **o es de otro tenant** (nunca 403, para no revelar que existe). |
| `company.tax_id.duplicate` | 409 | Ya hay una empresa con ese NIF en el tenant (al dar de alta o al editar). |
| `contact.not_found` | 404 | El contacto no existe, es de otra empresa o de otro tenant. |

## Añadir un código

1. Lanza una excepción de `com.norte.shared.domain.error` (`NotFoundException`, `ConflictException`,
   `ValidationException`, `ForbiddenException`, `StaleVersionException`) con un código `modulo.recurso.motivo` en minúsculas.
2. Añádelo a este catálogo en el mismo pull request.
3. Si el frontend debe mostrar un texto propio, añade la traducción con el código como clave.
