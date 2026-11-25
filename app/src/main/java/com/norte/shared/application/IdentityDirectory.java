package com.norte.shared.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Directorio de identidades (Keycloak) visto desde el alta de tenant. Todas las operaciones son idempotentes.
 */
public interface IdentityDirectory {

	/** Tenant de una organización ya creada con ese nombre exacto, si la hay. */
	Optional<UUID> findTenantIdByOrganizationName(String name);

	/** Crea la organización del tenant si no existe y devuelve su id. */
	String ensureOrganization(UUID tenantId, String name);

	/**
	 * Crea el usuario administrador si no existe, con roles {@code admin} y {@code user} y el atributo
	 * {@code tenant_id}, y lo hace miembro de la organización. Si ya existe con otro tenant, falla.
	 */
	AdminAccount ensureAdmin(String organizationId, UUID tenantId, AdminUser admin);

	record AdminUser(String username, String email, String firstName, String lastName) {
	}

	/**
	 * @param temporaryPassword contraseña temporal si se acaba de crear (Keycloak obliga a cambiarla en el primer
	 * acceso); vacía si el usuario ya existía
	 */
	record AdminAccount(String userId, Optional<String> temporaryPassword) {
	}

}
