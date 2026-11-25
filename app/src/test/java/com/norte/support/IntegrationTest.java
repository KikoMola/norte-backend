package com.norte.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.norte.TestcontainersConfiguration;
import com.norte.shared.application.TenantProvisioning;
import com.norte.shared.domain.access.BusinessModule;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.GrantedAuthority;
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

	/**
	 * Conexión como dueño de las tablas (norte_owner). TRUNCATE no pasa por RLS; para leer o tocar filas hay que fijar
	 * el tenant con {@link TestDatabase#asTenant}, porque FORCE ROW LEVEL SECURITY también se aplica al dueño.
	 */
	@Autowired
	protected TestDatabase database;

	@Autowired
	protected TenantProvisioning provisioning;

	/**
	 * Base de datos vacía con los tenants A y B dados de alta (sin Keycloak) y todos los módulos activos, como los
	 * deja el paso 1.1.
	 */
	@BeforeEach
	void cleanDatabase() {
		database.truncateBusinessTables();
		provisioning.provisionData(TENANT_A, "Tenant A", null, Set.of(BusinessModule.values()));
		provisioning.provisionData(TENANT_B, "Tenant B", null, Set.of(BusinessModule.values()));
	}

	/** Usuario del tenant indicado con rol {@code user}. */
	protected static JwtRequestPostProcessor userOf(UUID tenantId) {
		return userOf(tenantId, "ana");
	}

	protected static JwtRequestPostProcessor userOf(UUID tenantId, String username) {
		return userOf(tenantId, username, "user");
	}

	/** Usuario con los roles del realm indicados; sus permisos salen de {@code shared.role_permission}. */
	protected static JwtRequestPostProcessor userOf(UUID tenantId, String username, String... roles) {
		return jwt().jwt(token -> token.subject("user-" + username)
			.claim("preferred_username", username)
			.claim("name", username)
			.claim("email", username + "@norte.test")
			.claim("tenant_id", tenantId.toString())
			.claim("realm_access", Map.of("roles", List.of(roles))))
			.authorities(Arrays.stream(roles)
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
				.toList());
	}

	/** Usuario autenticado sin claim {@code tenant_id}. */
	protected static JwtRequestPostProcessor userWithoutTenant() {
		return jwt().jwt(token -> token.subject("user-without-tenant").claim("preferred_username", "nadie"));
	}

	/** Acceso de bajo nivel a la base de datos. */
	public static class TestDatabase {

		private final String url;

		private final JdbcTemplate owner;

		public TestDatabase(String url) {
			this.url = url;
			this.owner = new JdbcTemplate(new DriverManagerDataSource(url, OWNER, OWNER));
		}

		private static final String OWNER = "norte_owner";

		public void truncateBusinessTables() {
			owner.execute("TRUNCATE parties.company_aud, shared.company_settings_aud, shared.revision, shared.company_settings, parties.company, shared.idempotency_key, shared.role_permission, shared.tenant_module, "
					+ "shared.tenant");
		}

		/**
		 * Conexión como dueño con {@code app.tenant_id} fijado: ve y modifica solo las filas de ese tenant, como la
		 * aplicación. Cada consulta abre su propia conexión, así que el ajuste de sesión no se filtra a otras.
		 */
		public JdbcTemplate asTenant(UUID tenantId) {
			return new JdbcTemplate(new DriverManagerDataSource(url, OWNER, OWNER) {

				@Override
				protected Connection getConnectionFromDriver(String username, String password) throws SQLException {
					Connection connection = super.getConnectionFromDriver(username, password);
					try (var statement = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
						statement.setString(1, tenantId.toString());
						statement.execute();
					}
					return connection;
				}

			});
		}

	}

}
