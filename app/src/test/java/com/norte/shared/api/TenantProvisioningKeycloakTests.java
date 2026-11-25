package com.norte.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.norte.shared.application.IdentityDirectory.AdminUser;
import com.norte.shared.application.TenantProvisioning.Result;
import com.norte.shared.domain.access.BusinessModule;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Alta de tenant completa contra Keycloak real (paso 1.1): organización, primer administrador con su
 * {@code tenant_id} y sus roles, y datos en PostgreSQL. Repetirla no crea nada nuevo.
 */
class TenantProvisioningKeycloakTests extends IntegrationTest {

	private static final AdminUser GLORIA = new AdminUser("gloria", "gloria@globex.test", "Gloria", "Gómez");

	@DynamicPropertySource
	static void keycloak(DynamicPropertyRegistry registry) {
		registry.add("norte.keycloak.url", KeycloakSecurityTests.keycloak::getAuthServerUrl);
	}

	@Test
	void createsOrganizationAdminAndDataAndIsIdempotent() {
		Result first = provisioning.provision(null, "Globex Levante S.A.", GLORIA, Set.of(BusinessModule.INVOICING));

		RealmResource realm = realm();
		OrganizationRepresentation organization = realm.organizations().get(first.organizationId()).toRepresentation();
		assertThat(organization.getName()).isEqualTo("Globex Levante S.A.");
		assertThat(organization.getAttributes()).containsEntry("tenant_id", List.of(first.tenantId().toString()));

		UserRepresentation gloria = realm.users().searchByUsername("gloria", true).getFirst();
		assertThat(gloria.getAttributes()).containsEntry("tenant_id", List.of(first.tenantId().toString()));
		assertThat(realm.users().get(gloria.getId()).roles().realmLevel().listEffective())
			.extracting(RoleRepresentation::getName)
			.contains("admin", "user");
		assertThat(realm.organizations().get(first.organizationId()).members().list(0, 10))
			.extracting(UserRepresentation::getUsername)
			.containsExactly("gloria");
		assertThat(first.admin().temporaryPassword()).isPresent();
		assertThat(database.asTenant(first.tenantId())
			.queryForObject("select count(*) from shared.tenant where tenant_id = ?", Integer.class, first.tenantId()))
			.isEqualTo(1);

		// Segunda vez, sin tenant-id: reutiliza el tenant de la organización y no toca la contraseña.
		Result second = provisioning.provision(null, "Globex Levante S.A.", GLORIA, Set.of(BusinessModule.INVOICING));

		assertThat(second.tenantId()).isEqualTo(first.tenantId());
		assertThat(second.organizationId()).isEqualTo(first.organizationId());
		assertThat(second.admin().temporaryPassword()).isEmpty();
		assertThat(realm.organizations().search("Globex Levante S.A.", true, 0, 10)).hasSize(1);
		assertThat(realm.users().searchByUsername("gloria", true)).hasSize(1);
	}

	@Test
	void refusesToMoveAUserOfAnotherTenant() {
		var ana = new AdminUser("ana", "ana@acme.test", "Ana", "Acme");
		UUID other = UUID.fromString("77777777-7777-7777-7777-777777777777");

		assertThatThrownBy(() -> provisioning.provision(other, "Intrusa S.L.", ana, Set.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already belongs to tenant " + TENANT_A);
	}

	private static RealmResource realm() {
		return KeycloakSecurityTests.keycloak.getKeycloakAdminClient().realm("norte");
	}

}
