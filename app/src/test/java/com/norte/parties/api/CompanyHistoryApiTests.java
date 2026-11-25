package com.norte.parties.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.norte.parties.domain.CompanyRepository;
import com.norte.shared.application.TenantContext;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/** Historial de una empresa con Envers (paso 1.4). */
class CompanyHistoryApiTests extends IntegrationTest {

	@Autowired
	CompanyRepository companies;

	@Autowired
	TransactionTemplate transactions;

	@Test
	void creationIsRecordedWithItsAuthor() throws Exception {
		UUID id = createAcme();

		mvc.perform(get("/api/v1/companies/{id}/history", id).with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].action").value("created"))
			.andExpect(jsonPath("$.items[0].actor.id").value("user-ana"))
			.andExpect(jsonPath("$.items[0].actor.name").value("ana"))
			.andExpect(jsonPath("$.items[0].changes").isEmpty())
			.andExpect(jsonPath("$.pageInfo.hasNext").value(false));
	}

	@Test
	void updatesListTheFieldsThatChanged() throws Exception {
		UUID id = createAcme();
		rename(TENANT_A, id, "Acme Ibérica S.L.");

		mvc.perform(get("/api/v1/companies/{id}/history", id).with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].action").value("updated"))
			// Hecho fuera de una petición: sin usuario, es un cambio del sistema.
			.andExpect(jsonPath("$.items[0].actor").doesNotExist())
			.andExpect(jsonPath("$.items[0].changes", hasSize(1)))
			.andExpect(jsonPath("$.items[0].changes[0].field").value("name"))
			.andExpect(jsonPath("$.items[0].changes[0].from").value("Acme"))
			.andExpect(jsonPath("$.items[0].changes[0].to").value("Acme Ibérica S.L."))
			.andExpect(jsonPath("$.items[1].action").value("created"));
	}

	@Test
	void historyIsPaginatedFromTheMostRecentChange() throws Exception {
		UUID id = createAcme();
		rename(TENANT_A, id, "Acme 2");
		rename(TENANT_A, id, "Acme 3");
		rename(TENANT_A, id, "Acme 4");

		String first = mvc.perform(get("/api/v1/companies/{id}/history", id).param("limit", "2").with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items[0].changes[0].to").value("Acme 4"))
			.andExpect(jsonPath("$.items[1].changes[0].to").value("Acme 3"))
			.andExpect(jsonPath("$.pageInfo.hasNext").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String cursor = JsonPath.read(first, "$.pageInfo.nextCursor");

		mvc.perform(get("/api/v1/companies/{id}/history", id).param("limit", "2")
			.param("cursor", cursor)
			.with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(2)))
			// El cambio más antiguo de la segunda página se compara con el estado de la revisión anterior.
			.andExpect(jsonPath("$.items[0].changes[0].from").value("Acme"))
			.andExpect(jsonPath("$.items[0].changes[0].to").value("Acme 2"))
			.andExpect(jsonPath("$.items[1].action").value("created"))
			.andExpect(jsonPath("$.pageInfo.hasNext").value(false));
	}

	@Test
	void historyOfACompanyOfAnotherTenantIs404() throws Exception {
		UUID id = createAcme();

		mvc.perform(get("/api/v1/companies/{id}/history", id).with(userOf(TENANT_B, "bruno")))
			.andExpect(status().isNotFound());
	}

	@Test
	void auditTablesAreIsolatedByTenant() throws Exception {
		createAcme();

		// Las revisiones de la empresa; el alta del tenant deja otras (sus ajustes iniciales).
		String revisions = "select count(*) from shared.revision r join parties.company_aud a on a.rev = r.id";
		String audited = "select count(*) from parties.company_aud";
		assertThat(database.asTenant(TENANT_A).queryForObject(revisions, Integer.class)).isEqualTo(1);
		assertThat(database.asTenant(TENANT_A).queryForObject(audited, Integer.class)).isEqualTo(1);
		assertThat(database.asTenant(TENANT_B).queryForObject(revisions, Integer.class)).isZero();
		assertThat(database.asTenant(TENANT_B).queryForObject(audited, Integer.class)).isZero();
	}

	@Test
	void invalidCursorIs400() throws Exception {
		UUID id = createAcme();

		mvc.perform(get("/api/v1/companies/{id}/history", id).param("cursor", "bm9wZQ").with(userOf(TENANT_A)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("request.invalid_parameter"));
	}

	@Test
	void readingTheHistoryRequiresCompanyRead() throws Exception {
		UUID id = createAcme();

		mvc.perform(get("/api/v1/companies/{id}/history", id).with(userOf(TENANT_A, "pepe", "guest")))
			.andExpect(status().isForbidden());
	}

	private UUID createAcme() throws Exception {
		String body = mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private void rename(UUID tenantId, UUID id, String name) {
		TenantContext.runAs(tenantId, () -> transactions
			.executeWithoutResult(status -> companies.findById(id).orElseThrow().rename(name, Instant.now())));
	}

}
