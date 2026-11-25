package com.norte.shared.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.norte.shared.application.IdentityDirectory.AdminAccount;
import com.norte.shared.application.IdentityDirectory.AdminUser;
import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.Permission;
import com.norte.shared.domain.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Alta de tenant (paso 1.1). Idempotente: repetirla con los mismos datos no crea nada nuevo.
 *
 * <ol>
 * <li>En Keycloak, la organización y su primer usuario administrador.
 * <li>En PostgreSQL, el tenant, sus módulos, los permisos por defecto de cada rol y los datos iniciales de cada
 * módulo ({@link TenantSeeder}), todo en una transacción con el tenant fijado para RLS.
 * </ol>
 */
@Service
public class TenantProvisioning {

	private static final Logger log = LoggerFactory.getLogger(TenantProvisioning.class);

	private final IdentityDirectory directory;

	private final TenantRepository tenants;

	private final List<TenantSeeder> seeders;

	private final TransactionTemplate transactions;

	private final Clock clock;

	TenantProvisioning(IdentityDirectory directory, TenantRepository tenants, List<TenantSeeder> seeders,
			TransactionTemplate transactions, Clock clock) {
		this.directory = directory;
		this.tenants = tenants;
		this.seeders = seeders;
		this.transactions = transactions;
		this.clock = clock;
	}

	/**
	 * @param tenantId tenant a crear o completar; si es nulo se reutiliza el de la organización con ese nombre, y si no
	 * existe se genera uno nuevo
	 */
	public Result provision(UUID tenantId, String name, AdminUser admin, Set<BusinessModule> modules) {
		Objects.requireNonNull(admin, "admin");
		String tenantName = requireName(name);
		UUID id = Optional.ofNullable(tenantId)
			.or(() -> directory.findTenantIdByOrganizationName(tenantName))
			.orElseGet(UUID::randomUUID);
		String organizationId = directory.ensureOrganization(id, tenantName);
		AdminAccount account = directory.ensureAdmin(organizationId, id, admin);
		provisionData(id, tenantName, organizationId, modules);
		log.info("Tenant {} provisioned (organization {}, admin {})", id, organizationId, admin.username());
		return new Result(id, organizationId, account);
	}

	/** Solo la parte de base de datos del alta; la usan también los tests. */
	public void provisionData(UUID tenantId, String name, String organizationId, Set<BusinessModule> modules) {
		Objects.requireNonNull(tenantId, "tenantId");
		String tenantName = requireName(name);
		TenantContext.runAs(tenantId, () -> transactions.executeWithoutResult(status -> {
			var now = clock.instant();
			tenants.ensure(tenantId, tenantName, organizationId, now);
			tenants.enableModules(tenantId, modules, now);
			for (String role : Permission.defaultRoles()) {
				tenants.grant(tenantId, role, Permission.defaultsFor(role));
			}
			seeders.forEach(seeder -> seeder.seed(tenantId, tenantName));
		}));
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Tenant name is required");
		}
		return name.strip();
	}

	public record Result(UUID tenantId, String organizationId, AdminAccount admin) {
	}

}
