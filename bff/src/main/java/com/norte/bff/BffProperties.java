package com.norte.bff;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiUri URI del backend (norte-app) al que se reenvía /api/**.
 * @param postLoginRedirect a dónde vuelve el navegador tras iniciar sesión.
 * @param postLogoutRedirect a dónde vuelve el navegador tras cerrar sesión en Keycloak. Admite {baseUrl}.
 */
@ConfigurationProperties("norte.bff")
public record BffProperties(URI apiUri, String postLoginRedirect, String postLogoutRedirect) {
}
