package com.norte.shared.application;

import java.util.Set;
import java.util.UUID;

/**
 * Usuario de la petición en curso, extraído del JWT de Keycloak (paso 0.15).
 *
 * @param id {@code sub} del token
 * @param tenantId claim {@code tenant_id}; siempre presente y válido (si no, la petición se rechaza con 401)
 * @param roles roles del realm ({@code realm_access.roles})
 */
public record CurrentUser(String id, String username, String name, String email, UUID tenantId, Set<String> roles) {

	public CurrentUser {
		roles = Set.copyOf(roles);
	}

	public boolean hasRole(String role) {
		return roles.contains(role);
	}

}
