package com.norte.shared.infrastructure.keycloak;

import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.application.IdentityDirectory;
import com.norte.shared.application.TenantContext;
import com.norte.shared.domain.access.Permission;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * {@link IdentityDirectory} sobre la API de administración de Keycloak, con el cliente de servicio
 * {@code norte-provisioner} (flujo client credentials). Solo lo usa el alta de tenant.
 *
 * <p>Cada tenant es una <em>organización</em> de Keycloak con el atributo {@code tenant_id}. El claim
 * {@code tenant_id} del token sigue saliendo del atributo del usuario, que se fija al crearlo.
 */
@Component
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class KeycloakIdentityDirectory implements IdentityDirectory {

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
	};

	private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

	private final KeycloakAdminProperties properties;

	private final RestClient http;

	private final SecureRandom random = new SecureRandom();

	KeycloakIdentityDirectory(KeycloakAdminProperties properties) {
		this.properties = properties;
		this.http = RestClient.builder().baseUrl(properties.url()).build();
	}

	@Override
	public Optional<UUID> findTenantIdByOrganizationName(String name) {
		return admin().get()
			.uri("/admin/realms/{realm}/organizations?search={name}&exact=true&briefRepresentation=false",
					properties.realm(), name)
			.retrieve()
			.body(LIST)
			.stream()
			.filter(org -> name.equals(org.get("name")))
			.findFirst()
			.flatMap(KeycloakIdentityDirectory::tenantIdOf);
	}

	@Override
	public String ensureOrganization(UUID tenantId, String name) {
		Optional<Map<String, Object>> existing = admin().get()
			.uri("/admin/realms/{realm}/organizations?q={query}&briefRepresentation=false", properties.realm(),
					TenantContext.TENANT_CLAIM + ":" + tenantId)
			.retrieve()
			.body(LIST)
			.stream()
			.filter(org -> tenantIdOf(org).filter(tenantId::equals).isPresent())
			.findFirst();
		if (existing.isPresent()) {
			return (String) existing.get().get("id");
		}
		var organization = Map.of("name", name, "alias", "tenant-" + tenantId, "enabled", true, "attributes",
				Map.of(TenantContext.TENANT_CLAIM, List.of(tenantId.toString())));
		ResponseEntity<Void> created = admin().post()
			.uri("/admin/realms/{realm}/organizations", properties.realm())
			.contentType(MediaType.APPLICATION_JSON)
			.body(organization)
			.retrieve()
			.toBodilessEntity();
		return idFromLocation(created);
	}

	@Override
	public AdminAccount ensureAdmin(String organizationId, UUID tenantId, AdminUser admin) {
		Optional<Map<String, Object>> existing = findUser(admin.username());
		String userId;
		Optional<String> temporaryPassword = Optional.empty();
		if (existing.isPresent()) {
			userId = (String) existing.get().get("id");
			Optional<UUID> currentTenant = tenantIdOf(existing.get());
			if (currentTenant.isPresent() && !currentTenant.get().equals(tenantId)) {
				throw new IllegalStateException(
						"User " + admin.username() + " already belongs to tenant " + currentTenant.get());
			}
		}
		else {
			String password = randomPassword();
			userId = createUser(tenantId, admin, password);
			temporaryPassword = Optional.of(password);
		}
		assignRealmRoles(userId, Permission.defaultRoles());
		addMember(organizationId, userId);
		return new AdminAccount(userId, temporaryPassword);
	}

	private Optional<Map<String, Object>> findUser(String username) {
		return admin().get()
			.uri("/admin/realms/{realm}/users?username={username}&exact=true&briefRepresentation=false",
					properties.realm(), username)
			.retrieve()
			.body(LIST)
			.stream()
			.findFirst();
	}

	private String createUser(UUID tenantId, AdminUser admin, String password) {
		var user = Map.of("username", admin.username(), "email", admin.email(), "firstName", admin.firstName(),
				"lastName", admin.lastName(), "enabled", true, "attributes",
				Map.of(TenantContext.TENANT_CLAIM, List.of(tenantId.toString())), "credentials",
				List.of(Map.of("type", "password", "value", password, "temporary", true)));
		ResponseEntity<Void> created = admin().post()
			.uri("/admin/realms/{realm}/users", properties.realm())
			.contentType(MediaType.APPLICATION_JSON)
			.body(user)
			.retrieve()
			.toBodilessEntity();
		return idFromLocation(created);
	}

	private void assignRealmRoles(String userId, List<String> roleNames) {
		List<Map<String, Object>> roles = roleNames.stream()
			.map(role -> admin().get()
				.uri("/admin/realms/{realm}/roles/{role}", properties.realm(), role)
				.retrieve()
				.body(OBJECT))
			.toList();
		// Asignar un rol que ya tiene no es un error en Keycloak: la operación es idempotente.
		admin().post()
			.uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), userId)
			.contentType(MediaType.APPLICATION_JSON)
			.body(roles)
			.retrieve()
			.toBodilessEntity();
	}

	private void addMember(String organizationId, String userId) {
		try {
			admin().post()
				.uri("/admin/realms/{realm}/organizations/{org}/members", properties.realm(), organizationId)
				.contentType(MediaType.APPLICATION_JSON)
				.body("\"" + userId + "\"")
				.retrieve()
				.toBodilessEntity();
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() != HttpStatus.CONFLICT) {
				throw ex;
			}
			// Ya era miembro.
		}
	}

	/** Cliente con el token del cliente de servicio. Se pide uno por operación: el alta es un proceso puntual. */
	private RestClient admin() {
		var form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		Map<String, Object> token = http.post()
			.uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(OBJECT);
		String accessToken = (String) token.get("access_token");
		return http.mutate().defaultHeader("Authorization", "Bearer " + accessToken).build();
	}

	@SuppressWarnings("unchecked")
	private static Optional<UUID> tenantIdOf(Map<String, Object> representation) {
		if (!(representation.get("attributes") instanceof Map<?, ?> attributes)) {
			return Optional.empty();
		}
		if (!(attributes.get(TenantContext.TENANT_CLAIM) instanceof List<?> values) || values.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(String.valueOf(((List<Object>) values).getFirst())));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private static String idFromLocation(ResponseEntity<?> response) {
		URI location = response.getHeaders().getLocation();
		if (location == null) {
			throw new IllegalStateException("Keycloak did not return the Location of the created resource");
		}
		String path = location.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private String randomPassword() {
		var password = new StringBuilder(20);
		for (int i = 0; i < 20; i++) {
			password.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
		}
		return password.toString();
	}

}
