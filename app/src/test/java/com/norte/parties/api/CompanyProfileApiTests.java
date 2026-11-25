package com.norte.parties.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Empresa completa: perfil, edición con If-Match y posibles duplicados (paso 1.10). */
class CompanyProfileApiTests extends IntegrationTest {

	private static final String TURIA = """
			{
			  "name": "Distribuciones Turia S.L.",
			  "taxId": "B46000006",
			  "tradeName": "Turia",
			  "email": "Facturas@TURIA.test",
			  "phone": "961 52 38 40",
			  "address": {"line1": "Polígono Fuente del Jarro, calle 4", "postalCode": "46940", "city": "Manises",
			              "province": "Valencia", "country": "ES"},
			  "creditLimit": {"amount": "10000", "currency": "EUR"},
			  "isCustomer": true,
			  "isSupplier": true
			}""";

	@Test
	void createsACompanyWithItsWholeProfile() throws Exception {
		mvc.perform(create(TURIA))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tradeName").value("Turia"))
			.andExpect(jsonPath("$.email").value("Facturas@turia.test"))
			.andExpect(jsonPath("$.address.city").value("Manises"))
			.andExpect(jsonPath("$.address.country").value("ES"))
			.andExpect(jsonPath("$.creditLimit.amount").value("10000.00"))
			.andExpect(jsonPath("$.creditLimit.currency").value("EUR"))
			.andExpect(jsonPath("$.isCustomer").value(true))
			.andExpect(jsonPath("$.isSupplier").value(true));
	}

	@Test
	void theMinimalCreationIsAStillACustomerInSpain() throws Exception {
		mvc.perform(create("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.address.country").value("ES"))
			.andExpect(jsonPath("$.isCustomer").value(true))
			.andExpect(jsonPath("$.isSupplier").value(false))
			.andExpect(jsonPath("$.creditLimit").doesNotExist());
	}

	@Test
	void updatesWithIfMatchAndRecordsTheChanges() throws Exception {
		UUID id = idOf(mvc.perform(create(TURIA)).andReturn().getResponse().getContentAsString());
		String updated = TURIA.replace("Manises", "Paterna").replace("\"10000\"", "\"15000.50\"");

		mvc.perform(update(id, "\"0\"", updated))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.address.city").value("Paterna"))
			.andExpect(jsonPath("$.creditLimit.amount").value("15000.50"));

		mvc.perform(get("/api/v1/companies/{id}/history", id).with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items[0].changes[*].field").value(hasItem("address.city")))
			.andExpect(jsonPath("$.items[0].changes[*].field").value(hasItem("creditLimit")));
	}

	@Test
	void updatingAnOldVersionIs412() throws Exception {
		UUID id = idOf(mvc.perform(create(TURIA)).andReturn().getResponse().getContentAsString());
		mvc.perform(update(id, "\"0\"", TURIA.replace("Manises", "Paterna"))).andExpect(status().isOk());

		mvc.perform(update(id, "\"0\"", TURIA))
			.andExpect(status().isPreconditionFailed())
			.andExpect(jsonPath("$.code").value("concurrency.precondition_failed"));
	}

	@Test
	void theTaxIdOfAnotherCompanyIsADuplicate() throws Exception {
		mvc.perform(create("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}")).andExpect(status().isCreated());
		UUID id = idOf(mvc.perform(create(TURIA)).andReturn().getResponse().getContentAsString());

		mvc.perform(update(id, "\"0\"", TURIA.replace("B46000006", "B12345674")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("company.tax_id.duplicate"));
	}

	@Test
	void validationErrorsComeTogether() throws Exception {
		String invalid = TURIA.replace("\"isCustomer\": true", "\"isCustomer\": false")
			.replace("\"isSupplier\": true", "\"isSupplier\": false")
			.replace("46940", "99999")
			.replace("\"EUR\"", "\"USD\"");

		mvc.perform(create(invalid))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("creditLimit.currency"));
		mvc.perform(create(invalid.replace("\"USD\"", "\"EUR\"")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("isCustomer")))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem("address.postalCode")));
	}

	@Test
	void warnsAboutPossibleDuplicatesByTaxIdOrSimilarName() throws Exception {
		UUID acme = idOf(mvc.perform(create("{\"name\":\"Acme Ibérica S.L.\",\"taxId\":\"B12345674\"}"))
			.andReturn()
			.getResponse()
			.getContentAsString());
		mvc.perform(create("{\"name\":\"Globex Levante S.A.\",\"taxId\":\"A58818501\"}"))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/companies/duplicates").param("name", "acme iberica").with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].company.name").value("Acme Ibérica S.L."))
			.andExpect(jsonPath("$.items[0].reason").value("similar_name"));
		mvc.perform(get("/api/v1/companies/duplicates").param("name", "Otra")
			.param("taxId", "a-58818501")
			.with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items[0].company.name").value("Globex Levante S.A."))
			.andExpect(jsonPath("$.items[0].reason").value("tax_id"));
		// Al editar, la propia empresa no es un duplicado de sí misma.
		mvc.perform(get("/api/v1/companies/duplicates").param("name", "Acme Ibérica S.L.")
			.param("excludeId", acme.toString())
			.with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items").isEmpty());
		// Ni se ven las de otro tenant.
		mvc.perform(get("/api/v1/companies/duplicates").param("name", "Acme").with(userOf(TENANT_B, "bruno")))
			.andExpect(jsonPath("$.items").isEmpty());
	}

	private static MockHttpServletRequestBuilder create(String body) {
		return post("/api/v1/companies").with(userOf(TENANT_A)).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder update(UUID id, String ifMatch, String body) {
		return put("/api/v1/companies/{id}", id).with(userOf(TENANT_A))
			.header("If-Match", ifMatch)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private static UUID idOf(String body) {
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

}
