package com.norte.shared.infrastructure.audit;

import com.norte.shared.application.CurrentUser;
import com.norte.shared.application.TenantContext;
import org.hibernate.envers.RevisionListener;

/**
 * Rellena cada revisión con el tenant y el usuario de la petición. Lo crea Hibernate, no Spring: por eso lee el
 * contexto con los métodos estáticos de {@link TenantContext}.
 */
public class AuditRevisionListener implements RevisionListener {

	@Override
	public void newRevision(Object revision) {
		var user = TenantContext.currentUser();
		String name = user.map(AuditRevisionListener::displayName).orElse(null);
		((AuditRevision) revision).assign(TenantContext.requireTenantId(), user.map(CurrentUser::id).orElse(null),
				name);
	}

	private static String displayName(CurrentUser user) {
		return user.name() == null || user.name().isBlank() ? user.username() : user.name();
	}

}
