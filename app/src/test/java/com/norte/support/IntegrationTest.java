package com.norte.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.norte.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de los tests de integración (paso 0.19): contexto completo con PostgreSQL real en Testcontainers (mismos roles
 * que en local) y usuarios simulados con {@code jwt()} de spring-security-test. Los tests con Keycloak real están en
 * {@code KeycloakSecurityTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

	public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Autowired
	protected MockMvc mvc;

	/** Conexión como dueño de las tablas (norte_owner), solo para limpiar datos: TRUNCATE no pasa por RLS. */
	@Autowired
	protected TestDatabase database;

	@BeforeEach
	void cleanDatabase() {
		database.truncateBusinessTables();
	}

	/** Usuario del tenant indicado con rol {@code user}. */
	protected static JwtRequestPostProcessor userOf(UUID tenantId) {
		return userOf(tenantId, "ana");
	}

	protected static JwtRequestPostProcessor userOf(UUID tenantId, String username) {
		return jwt().jwt(token -> token.subject("user-" + username)
			.claim("preferred_username", username)
			.claim("name", username)
			.claim("email", username + "@norte.test")
			.claim("tenant_id", tenantId.toString())
			.claim("realm_access", Map.of("roles", List.of("user"))))
			.authorities(new SimpleGrantedAuthority("ROLE_user"));
	}

	/** Usuario autenticado sin claim {@code tenant_id}. */
	protected static JwtRequestPostProcessor userWithoutTenant() {
		return jwt().jwt(token -> token.subject("user-without-tenant").claim("preferred_username", "nadie"));
	}

	/** Acceso de bajo nivel a la base de datos. */
	public static class TestDatabase {

		private final JdbcTemplate owner;

		public TestDatabase(JdbcTemplate owner) {
			this.owner = owner;
		}

		public void truncateBusinessTables() {
			owner.execute("TRUNCATE parties.company");
		}

		public JdbcTemplate owner() {
			return owner;
		}

	}

}
