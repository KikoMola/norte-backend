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
| `concurrency.precondition_failed` | 412 | El `If-Match` no coincide con la versión actual: el recurso cambió desde que se leyó. |
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

## Códigos por módulo

### parties

| Código | Estado | Cuándo |
| --- | --- | --- |
| `company.not_found` | 404 | La empresa no existe **o es de otro tenant** (nunca 403, para no revelar que existe). |
| `company.tax_id.duplicate` | 409 | Ya hay una empresa con ese NIF en el tenant. |

## Añadir un código

1. Lanza una excepción de `com.norte.shared.domain.error` (`NotFoundException`, `ConflictException`,
   `ValidationException`, `ForbiddenException`) con un código `modulo.recurso.motivo` en minúsculas.
2. Añádelo a este catálogo en el mismo pull request.
3. Si el frontend debe mostrar un texto propio, añade la traducción con el código como clave.
