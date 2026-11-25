package com.norte.shared.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import com.norte.shared.domain.access.BusinessModule;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

/** Módulos contratados (paso 1.2) y permisos por acción (paso 1.3), de punta a punta. */
class AccessApiTests extends IntegrationTest {

	@Test
	void meListsEffectivePermissionsAndModules() throws Exception {
		mvc.perform(get("/api/v1/me").with(userOf(TENANT_A, "ana", "admin", "user")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItem("settings:edit")))
			.andExpect(jsonPath("$.permissions", hasItem("invoice:issue")))
			.andExpect(jsonPath("$.modules", hasItem("invoicing")));
	}

	@Test
	void plainUserCannotEditSettings() throws Exception {
		mvc.perform(get("/api/v1/me").with(userOf(TENANT_A, "eva", "user")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions", hasItem("company:edit")))
			.andExpect(jsonPath("$.permissions", not(hasItem("settings:edit"))));
	}

	@Test
	void roleWithoutPermissionsIsForbidden() throws Exception {
		mvc.perform(get("/api/v1/companies").with(userOf(TENANT_A, "pepe", "guest")))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("auth.forbidden"));
		mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A, "pepe", "guest"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/me").with(userOf(TENANT_A, "pepe", "guest")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.permissions").isEmpty());
	}

	@Test
	void permissionsAreConfiguredPerTenant() throws Exception {
		// En el tenant B, el rol «user» ya no puede dar de alta empresas.
		database.asTenant(TENANT_B)
			.update("delete from shared.role_permission where tenant_id = ? and role = 'user' and permission = ?",
					TENANT_B, "company:edit");

		mvc.perform(post("/api/v1/companies").with(userOf(TENANT_B, "bruno", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("auth.forbidden"));
		mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A, "ana", "user"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}")).andExpect(status().isCreated());
	}

	@Test
	void endpointOfADisabledModuleIs403() throws Exception {
		UUID erpOnly = UUID.fromString("33333333-3333-3333-3333-333333333333");
		provisioning.provisionData(erpOnly, "Solo ERP", null, Set.of(BusinessModule.INVOICING));

		mvc.perform(get("/api/v1/test-only/crm-probe").with(userOf(TENANT_A))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/test-only/crm-probe").with(userOf(erpOnly)))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("module.disabled"));
		// Lo común (empresas) funciona sin contratar nada más.
		mvc.perform(get("/api/v1/companies").with(userOf(erpOnly))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/me").with(userOf(erpOnly)))
			.andExpect(jsonPath("$.modules", containsInAnyOrder("invoicing")));
	}

	@Test
	void permissionsOfADisabledModuleAreNotGranted() throws Exception {
		UUID crmOnly = UUID.fromString("44444444-4444-4444-4444-444444444444");
		provisioning.provisionData(crmOnly, "Solo CRM", null, Set.of(BusinessModule.CRM));

		mvc.perform(get("/api/v1/me").with(userOf(crmOnly, "carla", "admin")))
			.andExpect(jsonPath("$.permissions", hasItem("company:edit")))
			.andExpect(jsonPath("$.permissions", not(hasItem("invoice:issue"))));
	}

	@Test
	void unknownTenantHasNoAccess() throws Exception {
		UUID unknown = UUID.fromString("55555555-5555-5555-5555-555555555555");
		mvc.perform(get("/api/v1/companies").with(userOf(unknown))).andExpect(status().isForbidden());
	}

}
