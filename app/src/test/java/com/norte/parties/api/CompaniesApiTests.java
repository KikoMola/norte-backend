package com.norte.parties.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

class CompaniesApiTests extends IntegrationTest {

	private static final String COMPANIES = "/api/v1/companies";

	@Test
	void createsCompanyAndReturnsLocationAndEtag() throws Exception {
		mvc.perform(post(COMPANIES).with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "  Acme Ibérica S.L. ", "taxId": "b-12345674"}"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", startsWith("/api/v1/companies/")))
			.andExpect(header().string("ETag", "\"0\""))
			.andExpect(jsonPath("$.id").value(notNullValue()))
			.andExpect(jsonPath("$.name").value("Acme Ibérica S.L."))
			.andExpect(jsonPath("$.taxId").value("B12345674"))
			.andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")));
	}

	@Test
	void getsCompanyById() throws Exception {
		String id = create(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		mvc.perform(get(COMPANIES + "/" + id).with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"0\""))
			.andExpect(jsonPath("$.name").value("Acme Ibérica S.L."));
	}

	@Test
	void anotherTenantGets404ForAnExistingCompany() throws Exception {
		String id = create(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		mvc.perform(get(COMPANIES + "/" + id).with(userOf(TENANT_B, "bruno")))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("company.not_found"));
	}

	@Test
	void anotherTenantDoesNotSeeCompaniesInTheList() throws Exception {
		create(TENANT_A, "Acme Ibérica S.L.", "B12345674");
		create(TENANT_B, "Globex Levante S.A.", "A58818501");

		mvc.perform(get(COMPANIES).with(userOf(TENANT_B, "bruno")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].name").value("Globex Levante S.A."));
	}

	@Test
	void sameTaxIdIsAllowedInDifferentTenants() throws Exception {
		create(TENANT_A, "Acme Ibérica S.L.", "B12345674");
		create(TENANT_B, "Acme en otro tenant", "B12345674");
	}

	@Test
	void duplicateTaxIdInSameTenantIsAConflict() throws Exception {
		create(TENANT_A, "Acme Ibérica S.L.", "B12345674");

		mvc.perform(post(COMPANIES).with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Otra Acme", "taxId": "B12345674"}"""))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("company.tax_id.duplicate"))
			.andExpect(jsonPath("$.type").value("https://norte.dev/problems/company-tax-id-duplicate"))
			.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void invalidTaxIdIs422WithFieldError() throws Exception {
		mvc.perform(post(COMPANIES).with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Acme", "taxId": "B12345675"}"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation"))
			.andExpect(jsonPath("$.errors[0].field").value("taxId"))
			.andExpect(jsonPath("$.errors[0].code").value("tax_id.invalid"));
	}

	@Test
	void missingFieldsAre422WithRequiredErrors() throws Exception {
		mvc.perform(post(COMPANIES).with(userOf(TENANT_A)).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors", hasSize(2)))
			.andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("required"))
			.andExpect(jsonPath("$.errors[?(@.field == 'taxId')].code").value("required"));
	}

	@Test
	void blankNameIs422() throws Exception {
		mvc.perform(post(COMPANIES).with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "   ", "taxId": "B12345674"}"""))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void malformedJsonIs400() throws Exception {
		mvc.perform(post(COMPANIES).with(userOf(TENANT_A)).contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("request.malformed"));
	}

	@Test
	void paginatesWithOpaqueCursor() throws Exception {
		create(TENANT_A, "Charlie S.L.", "B12345674");
		create(TENANT_A, "Alfa S.A.", "A58818501");
		create(TENANT_A, "Bravo", "12345678Z");

		MvcResult first = mvc.perform(get(COMPANIES).param("limit", "2").with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].name").value("Alfa S.A."))
			.andExpect(jsonPath("$.items[1].name").value("Bravo"))
			.andExpect(jsonPath("$.pageInfo.hasNext").value(true))
			.andExpect(jsonPath("$.pageInfo.nextCursor").value(notNullValue()))
			.andReturn();
		String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.pageInfo.nextCursor");

		mvc.perform(get(COMPANIES).param("limit", "2").param("cursor", cursor).with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].name").value("Charlie S.L."))
			.andExpect(jsonPath("$.pageInfo.hasNext").value(false))
			.andExpect(jsonPath("$.pageInfo.nextCursor").doesNotExist());
	}

	@Test
	void sortsDescendingByName() throws Exception {
		create(TENANT_A, "Alfa S.A.", "A58818501");
		create(TENANT_A, "Bravo", "12345678Z");

		mvc.perform(get(COMPANIES).param("sort", "-name").with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Bravo"));
	}

	@Test
	void searchesByNameOrTaxId() throws Exception {
		create(TENANT_A, "Acme Ibérica S.L.", "B12345674");
		create(TENANT_A, "Globex Levante S.A.", "A58818501");

		mvc.perform(get(COMPANIES).param("q", "ibér").with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].taxId").value("B12345674"));
		mvc.perform(get(COMPANIES).param("q", "a5881").with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].name").value("Globex Levante S.A."));
		mvc.perform(get(COMPANIES).param("q", "%").with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void rejectsUnknownSortAndInvalidCursorAndLimit() throws Exception {
		mvc.perform(get(COMPANIES).param("sort", "taxId").with(userOf(TENANT_A)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("request.invalid_parameter"));
		mvc.perform(get(COMPANIES).param("cursor", "garbage").with(userOf(TENANT_A)))
			.andExpect(status().isBadRequest());
		mvc.perform(get(COMPANIES).param("limit", "500").with(userOf(TENANT_A)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void unknownCompanyIs404() throws Exception {
		mvc.perform(get(COMPANIES + "/" + UUID.randomUUID()).with(userOf(TENANT_A)))
			.andExpect(status().isNotFound());
	}

	private String create(java.util.UUID tenant, String name, String taxId) throws Exception {
		JwtRequestPostProcessor user = userOf(tenant);
		MvcResult result = mvc.perform(post(COMPANIES).with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"%s\", \"taxId\": \"%s\"}".formatted(name, taxId)))
			.andExpect(status().isCreated())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

}
