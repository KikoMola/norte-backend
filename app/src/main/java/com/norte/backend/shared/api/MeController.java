package com.norte.backend.shared.api;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesión del usuario actual. Provisional hasta que el contrato OpenAPI genere la interfaz (pasos 0.22 y 0.23).
 */
@RestController
@RequestMapping("/api/v1")
class MeController {

	@GetMapping("/me")
	Me me(@AuthenticationPrincipal Jwt jwt) {
		return new Me(
				jwt.getSubject(),
				jwt.getClaimAsString("preferred_username"),
				jwt.getClaimAsString("name"),
				jwt.getClaimAsString("email"),
				jwt.getClaimAsString("tenant_id"),
				realmRoles(jwt));
	}

	private static List<String> realmRoles(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
			return List.of();
		}
		return roles.stream().map(String::valueOf).sorted().toList();
	}

	record Me(String id, String username, String name, String email, String tenantId, List<String> roles) {
	}

}
