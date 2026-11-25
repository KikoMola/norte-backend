package com.norte.shared.domain.access;

import java.util.Collection;
import java.util.Set;

/**
 * Lectura de módulos y permisos del tenant de la transacción actual. Como el resto de repositorios, no filtra por
 * tenant: lo hace PostgreSQL con RLS.
 */
public interface AccessRepository {

	Set<BusinessModule> enabledModules();

	/** Permisos asignados a cualquiera de {@code roles}; los códigos que ya no existen en el catálogo se ignoran. */
	Set<Permission> permissionsOf(Collection<String> roles);

}
