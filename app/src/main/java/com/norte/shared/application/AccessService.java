package com.norte.shared.application;

import java.util.Set;

import com.norte.shared.domain.access.AccessRepository;
import com.norte.shared.domain.access.TenantAccess;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Módulos y permisos efectivos del usuario actual (pasos 1.2 y 1.3). */
@Service
public class AccessService {

	private final AccessRepository access;

	AccessService(AccessRepository access) {
		this.access = access;
	}

	/** Sin usuario autenticado, ningún acceso. */
	@Transactional(readOnly = true)
	public TenantAccess current() {
		return TenantContext.currentUser().map(user -> of(user.roles())).orElse(TenantAccess.NONE);
	}

	private TenantAccess of(Set<String> roles) {
		return new TenantAccess(access.enabledModules(), access.permissionsOf(roles));
	}

}
