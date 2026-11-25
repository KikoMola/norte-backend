package com.norte.shared.domain.access;

import java.util.Arrays;
import java.util.Optional;

/**
 * Módulos que un tenant contrata (paso 1.2). Cada uno corresponde a un paquete {@code com.norte.<code>}: un endpoint
 * de ese paquete con el módulo inactivo responde 403 {@code module.disabled}.
 *
 * <p>{@code shared}, {@code parties} y {@code catalog} no están aquí: son comunes al CRM y al ERP y siempre están
 * activos. Por eso se puede usar el ERP sin el CRM.
 */
public enum BusinessModule {

	CRM("crm"),
	SALES("sales"),
	INVOICING("invoicing"),
	INVENTORY("inventory"),
	PURCHASING("purchasing"),
	TREASURY("treasury"),
	ACCOUNTING("accounting"),
	HR("hr");

	private static final String BASE_PACKAGE = "com.norte.";

	private final String code;

	BusinessModule(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<BusinessModule> fromCode(String code) {
		return Arrays.stream(values()).filter(module -> module.code.equals(code)).findFirst();
	}

	/** {@code com.norte.invoicing.api} → {@code INVOICING}; {@code com.norte.parties.api} → vacío (común). */
	public static Optional<BusinessModule> ofPackage(String packageName) {
		if (packageName == null || !packageName.startsWith(BASE_PACKAGE)) {
			return Optional.empty();
		}
		String rest = packageName.substring(BASE_PACKAGE.length());
		int dot = rest.indexOf('.');
		return fromCode(dot < 0 ? rest : rest.substring(0, dot));
	}

}
