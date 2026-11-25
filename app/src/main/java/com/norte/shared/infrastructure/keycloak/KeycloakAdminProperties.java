package com.norte.shared.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Acceso a la API de administración de Keycloak para el alta de tenant ({@code norte.keycloak.*}).
 *
 * @param url URL base de Keycloak, sin el realm
 * @param clientSecret secreto del cliente de servicio ({@code KEYCLOAK_PROVISIONER_SECRET})
 */
@ConfigurationProperties("norte.keycloak")
record KeycloakAdminProperties(String url, @DefaultValue("norte") String realm,
		@DefaultValue("norte-provisioner") String clientId, String clientSecret) {
}
