package com.norte.shared.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.Permission;
import com.norte.shared.domain.tenant.TenantRepository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTenantRepository implements TenantRepository {

	private final JdbcClient jdbc;

	JdbcTenantRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean exists(UUID tenantId) {
		return jdbc.sql("select count(*) from shared.tenant where tenant_id = ?")
			.param(tenantId)
			.query(Integer.class)
			.single() > 0;
	}

	@Override
	public void ensure(UUID tenantId, String name, String keycloakOrganizationId, Instant now) {
		jdbc.sql("""
				insert into shared.tenant (tenant_id, name, keycloak_organization_id, created_at)
				values (?, ?, ?, ?)
				on conflict (tenant_id) do update
				  set keycloak_organization_id = coalesce(shared.tenant.keycloak_organization_id,
				                                          excluded.keycloak_organization_id)""")
			.params(tenantId, name, keycloakOrganizationId, Timestamp.from(now))
			.update();
	}

	@Override
	public void enableModules(UUID tenantId, Collection<BusinessModule> modules, Instant now) {
		for (BusinessModule module : modules) {
			jdbc.sql("""
					insert into shared.tenant_module (tenant_id, module, enabled_at) values (?, ?, ?)
					on conflict do nothing""")
				.params(tenantId, module.code(), Timestamp.from(now))
				.update();
		}
	}

	@Override
	public void grant(UUID tenantId, String role, Collection<Permission> permissions) {
		for (Permission permission : permissions) {
			jdbc.sql("""
					insert into shared.role_permission (tenant_id, role, permission) values (?, ?, ?)
					on conflict do nothing""")
				.params(tenantId, role, permission.code())
				.update();
		}
	}

}
