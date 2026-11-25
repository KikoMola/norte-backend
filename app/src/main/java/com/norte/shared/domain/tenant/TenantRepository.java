package com.norte.shared.domain.tenant;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.Permission;

/**
 * Datos de plataforma del tenant de la transacción actual. Todas las escrituras son idempotentes («si no existe»),
 * porque el alta de tenant se puede repetir sin efectos de más (paso 1.1).
 */
public interface TenantRepository {

	boolean exists(UUID tenantId);

	/** Crea el tenant si no existe; si existe, solo completa la organización de Keycloak si faltaba. */
	void ensure(UUID tenantId, String name, String keycloakOrganizationId, Instant now);

	/** Activa los módulos que falten; no desactiva ninguno. */
	void enableModules(UUID tenantId, Collection<BusinessModule> modules, Instant now);

	/** Concede al rol los permisos que le falten; no quita ninguno. */
	void grant(UUID tenantId, String role, Collection<Permission> permissions);

}
