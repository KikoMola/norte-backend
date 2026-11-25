package com.norte.shared.domain.access;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de permisos por acción (paso 1.3), con forma {@code resource:action}. El código del permiso viaja en
 * {@code GET /me} y lo usa la directiva {@code *appCan} del frontend, así que es estable: no se renombra.
 *
 * <p>Un permiso de un módulo contratable solo se concede si el tenant tiene ese módulo activo.
 */
public enum Permission {

	COMPANY_READ("company:read", null),
	COMPANY_EDIT("company:edit", null),

	PRODUCT_READ("product:read", null),
	PRODUCT_EDIT("product:edit", null),

	SETTINGS_READ("settings:read", null),
	SETTINGS_EDIT("settings:edit", null),

	INVOICE_READ("invoice:read", BusinessModule.INVOICING),
	/** Crear y modificar borradores. */
	INVOICE_EDIT("invoice:edit", BusinessModule.INVOICING),
	/** Emitir: asigna número y la factura pasa a ser inmutable. */
	INVOICE_ISSUE("invoice:issue", BusinessModule.INVOICING),
	INVOICE_SEND("invoice:send", BusinessModule.INVOICING),
	/** Registrar cobros. */
	INVOICE_COLLECT("invoice:collect", BusinessModule.INVOICING);

	private final String code;

	private final BusinessModule module;

	Permission(String code, BusinessModule module) {
		this.code = code;
		this.module = module;
	}

	public String code() {
		return code;
	}

	/** Módulo al que pertenece; vacío si es común. */
	public Optional<BusinessModule> module() {
		return Optional.ofNullable(module);
	}

	public static Optional<Permission> fromCode(String code) {
		return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
	}

	/**
	 * Permisos con los que nace cada rol del realm en un tenant nuevo. Después, cada tenant puede cambiarlos.
	 *
	 * <ul>
	 * <li>{@code admin}: todo.
	 * <li>{@code user}: todo salvo cambiar los ajustes de la empresa.
	 * </ul>
	 */
	public static Set<Permission> defaultsFor(String role) {
		return switch (role) {
			case Roles.ADMIN -> Set.of(values());
			case Roles.USER -> Arrays.stream(values())
				.filter(permission -> permission != SETTINGS_EDIT)
				.collect(Collectors.toUnmodifiableSet());
			default -> Set.of();
		};
	}

	/** Roles del realm con permisos por defecto. */
	public static List<String> defaultRoles() {
		return List.of(Roles.ADMIN, Roles.USER);
	}

	/** Roles del realm de Keycloak ({@code infra/keycloak/realm-norte.json}). */
	public static final class Roles {

		public static final String ADMIN = "admin";

		public static final String USER = "user";

		private Roles() {
		}

	}

}
