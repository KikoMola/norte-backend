package com.norte.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import com.norte.shared.domain.access.BusinessModule;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Parte de base de datos del alta de tenant (paso 1.1): idempotente y aislada por RLS. */
class TenantProvisioningTests extends IntegrationTest {

	private static final UUID NEW_TENANT = UUID.fromString("66666666-6666-6666-6666-666666666666");

	/** Conexión de la aplicación (norte_app), sujeta a RLS. */
	@Autowired
	JdbcTemplate app;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void provisioningTwiceCreatesNothingNew() {
		provisioning.provisionData(NEW_TENANT, "Acme Ibérica S.L.", "org-1", Set.of(BusinessModule.INVOICING));
		var first = snapshot(NEW_TENANT);

		provisioning.provisionData(NEW_TENANT, "Acme Ibérica S.L.", "org-1", Set.of(BusinessModule.INVOICING));

		assertThat(snapshot(NEW_TENANT)).isEqualTo(first);
		assertThat(first.tenants()).isEqualTo(1);
		assertThat(first.modules()).isEqualTo(1);
		assertThat(first.permissions()).isPositive();
	}

	@Test
	void reprovisioningAddsModulesButNeverRemovesThem() {
		provisioning.provisionData(NEW_TENANT, "Acme", null, Set.of(BusinessModule.INVOICING));
		provisioning.provisionData(NEW_TENANT, "Acme", "org-1", Set.of(BusinessModule.CRM));

		assertThat(snapshot(NEW_TENANT).modules()).isEqualTo(2);
		assertThat(database.asTenant(NEW_TENANT)
			.queryForObject("select keycloak_organization_id from shared.tenant where tenant_id = ?", String.class,
					NEW_TENANT))
			.isEqualTo("org-1");
	}

	@Test
	void tenantSeesOnlyItsOwnPlatformRows() throws Exception {
		Integer visible = TenantContext.callAs(TENANT_A,
				() -> transactions.execute(status -> app.queryForObject(
						"select count(*) from shared.tenant", Integer.class)));
		Integer modulesOfB = TenantContext.callAs(TENANT_A,
				() -> transactions.execute(status -> app.queryForObject(
						"select count(*) from shared.tenant_module where tenant_id = ?", Integer.class, TENANT_B)));

		assertThat(visible).isEqualTo(1);
		assertThat(modulesOfB).isZero();
	}

	private Snapshot snapshot(UUID tenantId) {
		var owner = database.asTenant(tenantId);
		return new Snapshot(
				owner.queryForObject("select count(*) from shared.tenant where tenant_id = ?", Integer.class, tenantId),
				owner.queryForObject("select count(*) from shared.tenant_module where tenant_id = ?", Integer.class,
						tenantId),
				owner.queryForObject("select count(*) from shared.role_permission where tenant_id = ?", Integer.class,
						tenantId));
	}

	record Snapshot(int tenants, int modules, int permissions) {
	}

}
