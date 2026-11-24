package com.norte.shared.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tenant y usuario de la ejecución actual.
 *
 * <p>En una petición HTTP salen del JWT. Para procesos sin petición (tareas programadas, tests) se puede fijar un
 * tenant con {@link #callAs(UUID, Callable)}, que usa un {@link ScopedValue} y por tanto funciona con hilos virtuales.
 *
 * <p>El tenant de aquí es el que se fija en PostgreSQL con {@code SET LOCAL app.tenant_id} al empezar cada transacción
 * (ver {@code TenantAwareJpaTransactionManager}).
 */
public final class TenantContext {

	public static final String TENANT_CLAIM = "tenant_id";

	private static final ScopedValue<UUID> TENANT_OVERRIDE = ScopedValue.newInstance();

	private TenantContext() {
	}

	/** Tenant actual, si lo hay. */
	public static Optional<UUID> currentTenantId() {
		if (TENANT_OVERRIDE.isBound()) {
			return Optional.of(TENANT_OVERRIDE.get());
		}
		return currentJwt().flatMap(TenantContext::tenantIdOf);
	}

	/** Tenant actual; falla si no hay ninguno. */
	public static UUID requireTenantId() {
		return currentTenantId().orElseThrow(() -> new IllegalStateException("No tenant in the current context"));
	}

	/** Usuario de la petición actual, si la petición viene autenticada con un JWT con tenant válido. */
	public static Optional<CurrentUser> currentUser() {
		return currentJwt().flatMap(jwt -> tenantIdOf(jwt).map(tenantId -> new CurrentUser(jwt.getSubject(),
				jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"),
				tenantId, realmRoles(jwt))));
	}

	/** Ejecuta {@code task} con {@code tenantId} como tenant actual, aunque no haya petición HTTP. */
	public static <T> T callAs(UUID tenantId, Callable<T> task) throws Exception {
		return ScopedValue.where(TENANT_OVERRIDE, tenantId).call(task::call);
	}

	/** Variante de {@link #callAs(UUID, Callable)} sin valor de retorno. */
	public static void runAs(UUID tenantId, Runnable task) {
		ScopedValue.where(TENANT_OVERRIDE, tenantId).run(task);
	}

	/** Lee y valida el claim {@code tenant_id}: tiene que ser un UUID. */
	public static Optional<UUID> tenantIdOf(Jwt jwt) {
		String raw = jwt.getClaimAsString(TENANT_CLAIM);
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(raw));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	public static Set<String> realmRoles(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
			return Set.of();
		}
		Set<String> result = new LinkedHashSet<>();
		roles.forEach(role -> result.add(String.valueOf(role)));
		return result;
	}

	private static Optional<Jwt> currentJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			return Optional.of(token.getToken());
		}
		return Optional.empty();
	}

}
