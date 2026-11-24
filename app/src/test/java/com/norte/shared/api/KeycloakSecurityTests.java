package com.norte.shared.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.norte.support.IntegrationTest;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tests de seguridad con Keycloak real y el realm de {@code infra/keycloak} (paso 0.19): firma, emisor, audiencia
 * {@code norte-api} y claim {@code tenant_id} se validan de verdad, sin {@code jwt()} simulado.
 */
class KeycloakSecurityTests extends IntegrationTest {

	static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8.2";

	static final KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
		.withRealmImportFile("/realm-norte.json");

	static {
		keycloak.start();
	}

	private static final HttpClient http = HttpClient.newHttpClient();

	private static final JsonMapper json = JsonMapper.builder().build();

	@DynamicPropertySource
	static void issuer(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakSecurityTests::issuer);
	}

	@BeforeAll
	static void prepareRealm() {
		RealmResource realm = keycloak.getKeycloakAdminClient().realm("norte");
		// Solo en el test: permite pedir tokens con usuario y contraseña sin pasar por el navegador.
		var bff = realm.clients().findByClientId("norte-bff").getFirst();
		bff.setDirectAccessGrantsEnabled(true);
		realm.clients().get(bff.getId()).update(bff);

		if (realm.users().searchByUsername("sintenant", true).isEmpty()) {
			var user = new UserRepresentation();
			user.setUsername("sintenant");
			user.setEmail("sintenant@norte.test");
			user.setFirstName("Sin");
			user.setLastName("Tenant");
			user.setEnabled(true);
			user.setEmailVerified(true);
			var password = new CredentialRepresentation();
			password.setType(CredentialRepresentation.PASSWORD);
			password.setValue("sintenant");
			password.setTemporary(false);
			user.setCredentials(List.of(password));
			realm.users().create(user).close();
		}
	}

	@Test
	void realTokenCarriesTheTenantOfTheUser() throws Exception {
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer("ana")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("ana"))
			.andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()));
	}

	@Test
	void brunoDoesNotSeeCompaniesOfAna() throws Exception {
		mvc.perform(post("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, bearer("ana"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Acme Ibérica S.L.", "taxId": "B12345674"}"""))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, bearer("bruno")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, bearer("ana")))
			.andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void tokenIssuedForAnotherClientIsRejectedByAudience() throws Exception {
		// admin-cli no tiene el mapper de audiencia norte-api.
		String token = token(Map.of("grant_type", "password", "client_id", "admin-cli", "username", "ana",
				"password", "ana"));

		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("auth.unauthenticated"));
	}

	@Test
	void userWithoutTenantIsRejected() throws Exception {
		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer("sintenant")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("auth.tenant_missing"));
	}

	@Test
	void tamperedTokenIsRejected() throws Exception {
		String token = bearer("ana");
		String tampered = token.substring(0, token.length() - 4) + "AAAA";

		mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, tampered))
			.andExpect(status().isUnauthorized());
	}

	private static String issuer() {
		return keycloak.getAuthServerUrl() + "/realms/norte";
	}

	private static String bearer(String username) throws IOException, InterruptedException {
		return "Bearer " + token(Map.of("grant_type", "password", "client_id", "norte-bff", "client_secret",
				"norte-bff-dev-secret", "username", username, "password", username, "scope", "openid"));
	}

	private static String token(Map<String, String> form) throws IOException, InterruptedException {
		String body = form.entrySet()
			.stream()
			.map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		var request = HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Token request failed: " + response.statusCode() + " " + response.body());
		}
		return json.readTree(response.body()).path("access_token").asString();
	}

}
