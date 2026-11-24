# 0002. Autenticación en el navegador con un BFF

- **Estado:** Aceptado
- **Fecha:** 2026-11-24
- **Paso del planning:** 0.2

## Contexto

Norte maneja datos financieros y fiscales de varias empresas (multi-tenant). Una SPA que guarda access tokens en
memoria o en `localStorage` los expone a cualquier XSS, y un token robado da acceso a la API hasta que caduca.

## Decisión

Un **Backend for Frontend** (subproyecto `bff/`, Spring Cloud Gateway Server Web MVC) entre el navegador y la API:

- El BFF hace el login OIDC contra Keycloak (authorization code con **PKCE**) como cliente confidencial
  (`norte-bff`) y guarda los tokens en la sesión del servidor.
- El navegador solo tiene la cookie `NORTE_SESSION`: `HttpOnly`, `SameSite=Lax` y `Secure` en producción. **Los
  tokens nunca llegan a JavaScript.**
- `/api/**` se reenvía a `app` con el access token como `Bearer` (token relay) y sin la cookie. El BFF renueva el
  token con el refresh token cuando caduca.
- **CSRF:** cookie `XSRF-TOKEN` legible por JavaScript y cabecera `X-XSRF-TOKEN` en cada petición que modifica
  datos (`csrf.spa()` de Spring Security). Angular `HttpClient` lo hace solo.
- `/api/**` sin sesión responde `401`, no redirige: la SPA decide si inicia sesión.
- `POST /logout` responde `202` con la URL de cierre de sesión de Keycloak en `Location`, porque una petición XHR
  no puede seguir la redirección a otro origen. La SPA navega a esa URL.
- En desarrollo, el servidor de Angular reenvía `/api`, `/oauth2`, `/login/oauth2` y `/logout` al BFF; en
  producción lo hace Caddy. El navegador lo ve todo en el mismo origen, así que no hace falta CORS.
- `app` es un resource server que valida el JWT (emisor del realm `norte`, audiencia `norte-api`).

## Consecuencias

- Un XSS ya no puede robar tokens; como mucho puede hacer peticiones mientras la página esté abierta.
- Hay un servicio más que desplegar y observar.
- La sesión vive en memoria del BFF: reiniciarlo cierra las sesiones y no permite varias instancias. Cuando haga
  falta escalar (hito 9) se pasa a Spring Session con JDBC o Redis.
- Los interceptores del frontend no gestionan tokens, solo los `401`.

## Alternativas descartadas

- **Tokens en la SPA (angular-oauth2-oidc o similar):** más simple, pero expone tokens a XSS. Para un ERP con datos
  financieros no compensa.
- **Login dentro de `app` (oauth2Login en el mismo servicio):** evita un servicio, pero mezcla sesiones de navegador
  con una API sin estado y complica escalar `app` por separado.
