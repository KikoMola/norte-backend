package com.norte.shared.domain.access;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Qué puede hacer el usuario actual en su tenant: los módulos que el tenant tiene contratados y los permisos de sus
 * roles. Un permiso de un módulo no contratado no cuenta, aunque esté asignado al rol.
 */
public record TenantAccess(Set<BusinessModule> modules, Set<Permission> permissions) {

	public static final TenantAccess NONE = new TenantAccess(Set.of(), Set.of());

	public TenantAccess {
		modules = modules.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(modules));
		var effective = EnumSet.noneOf(Permission.class);
		for (Permission permission : permissions) {
			if (permission.module().map(modules::contains).orElse(true)) {
				effective.add(permission);
			}
		}
		permissions = Set.copyOf(effective);
	}

	public static TenantAccess of(Collection<BusinessModule> modules, Collection<Permission> permissions) {
		return new TenantAccess(Set.copyOf(modules), Set.copyOf(permissions));
	}

	public boolean hasModule(BusinessModule module) {
		return modules.contains(module);
	}

	public boolean can(Permission permission) {
		return permissions.contains(permission);
	}

}
