package com.norte.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.norte.shared.application.TenantContext;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test obligatorio del paso 0.16, a nivel de base de datos: con el usuario de la aplicación y el tenant fijado por el
 * gestor de transacciones, el tenant A no lee ni escribe datos del tenant B. Si alguien quita el RLS, la política o el
 * FORCE, este test falla aunque el código Java filtre bien.
 */
class TenantIsolationTests extends IntegrationTest {

	private static final String INSERT = """
			insert into parties.company (tenant_id, id, name, tax_id, version, created_at, updated_at)
			values (?, ?, ?, ?, 0, ?, ?)""";

	@Autowired
	JdbcTemplate app;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void applicationRoleCannotBypassRowLevelSecurity() {
		var role = app.queryForMap("""
				select current_user as name, r.rolsuper, r.rolbypassrls,
				       (select c.relowner = r.oid from pg_class c where c.oid = 'parties.company'::regclass) as owner,
				       (select c.relrowsecurity and c.relforcerowsecurity from pg_class c
				         where c.oid = 'parties.company'::regclass) as rls_forced
				from pg_roles r where r.rolname = current_user""");

		assertThat(role).containsEntry("name", "norte_app")
			.containsEntry("rolsuper", false)
			.containsEntry("rolbypassrls", false)
			.containsEntry("owner", false)
			.containsEntry("rls_forced", true);
	}

	@Test
	void tenantDoesNotSeeRowsOfAnotherTenant() {
		UUID companyOfA = insertAs(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		assertThat(countAs(TENANT_A)).isEqualTo(1);
		assertThat(countAs(TENANT_B)).isZero();
		assertThat(inTenant(TENANT_B, () -> app.queryForObject(
				"select count(*) from parties.company where id = ?", Integer.class, companyOfA)))
			.isZero();
	}

	@Test
	void tenantCannotUpdateOrDeleteRowsOfAnotherTenant() {
		UUID companyOfA = insertAs(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		int updated = inTenant(TENANT_B,
				() -> app.update("update parties.company set name = 'Robada' where id = ?", companyOfA));
		int deleted = inTenant(TENANT_B, () -> app.update("delete from parties.company where id = ?", companyOfA));

		assertThat(updated).isZero();
		assertThat(deleted).isZero();
		assertThat(inTenant(TENANT_A, () -> app.queryForObject(
				"select name from parties.company where id = ?", String.class, companyOfA)))
			.isEqualTo("Acme Ibérica S.L.");
	}

	@Test
	void tenantCannotInsertRowsForAnotherTenant() {
		assertThatThrownBy(() -> inTenant(TENANT_B, () -> app.update(INSERT, TENANT_A, UUID.randomUUID(), "Intrusa",
				"A58818501", now(), now())))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("row-level security");
	}

	@Test
	void withoutTenantNothingIsVisibleNorWritable() {
		insertAs(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		Integer visible = transactions.execute(status -> app.queryForObject(
				"select count(*) from parties.company", Integer.class));

		assertThat(visible).isZero();
		assertThatThrownBy(() -> transactions.executeWithoutResult(status -> app.update(INSERT, TENANT_A,
				UUID.randomUUID(), "Sin tenant", "A58818501", now(), now())))
			.isInstanceOf(DataAccessException.class);
	}

	@Test
	void tenantSettingDoesNotLeakBetweenTransactions() {
		insertAs(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		// La misma conexión del pool, después de una transacción del tenant A, no conserva su valor.
		for (int i = 0; i < 5; i++) {
			assertThat(countAs(TENANT_A)).isEqualTo(1);
			String setting = transactions.execute(status -> app.queryForObject(
					"select coalesce(nullif(current_setting('app.tenant_id', true), ''), 'none')", String.class));
			assertThat(setting).isEqualTo("none");
		}
	}

	private UUID insertAs(UUID tenantId, String name, String taxId) {
		UUID id = UUID.randomUUID();
		inTenant(tenantId, () -> app.update(INSERT, tenantId, id, name, taxId, now(), now()));
		return id;
	}

	private int countAs(UUID tenantId) {
		return inTenant(tenantId, () -> app.queryForObject("select count(*) from parties.company", Integer.class));
	}

	private <T> T inTenant(UUID tenantId, java.util.function.Supplier<T> work) {
		try {
			return TenantContext.callAs(tenantId, () -> transactions.execute(status -> work.get()));
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Timestamp now() {
		return Timestamp.from(Instant.now());
	}

}
