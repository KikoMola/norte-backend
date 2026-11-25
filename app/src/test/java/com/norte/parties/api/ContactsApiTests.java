package com.norte.parties.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Contactos de una empresa con su función (paso 1.10). */
class ContactsApiTests extends IntegrationTest {

	private static final String LUCIA = """
			{"firstName": "Lucía", "lastName": "Serrano", "jobTitle": "Responsable de administración",
			 "email": "lserrano@TURIA.test", "phone": "961 52 38 40", "role": "billing"}""";

	private UUID company;

	@BeforeEach
	void createCompany() throws Exception {
		String body = mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Distribuciones Turia S.L.\",\"taxId\":\"B46000006\"}"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		company = UUID.fromString(JsonPath.read(body, "$.id"));
	}

	@Test
	void addsContactsAndListsThemByName() throws Exception {
		mvc.perform(create(company, LUCIA))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.email").value("lserrano@turia.test"))
			.andExpect(jsonPath("$.role").value("billing"))
			.andExpect(jsonPath("$.companyId").value(company.toString()));
		mvc.perform(create(company, "{\"firstName\": \"Pablo\", \"role\": \"decision_maker\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lastName").doesNotExist());

		mvc.perform(get("/api/v1/companies/{id}/contacts", company).with(userOf(TENANT_A)))
			.andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].firstName").value("Lucía"))
			.andExpect(jsonPath("$.items[1].firstName").value("Pablo"));
	}

	@Test
	void updatesWithIfMatchAndDeletes() throws Exception {
		String contact = JsonPath.read(mvc.perform(create(company, LUCIA)).andReturn().getResponse().getContentAsString(),
				"$.id");
		String path = "/api/v1/companies/" + company + "/contacts/" + contact;

		mvc.perform(put(path).with(userOf(TENANT_A))
			.header("If-Match", "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(LUCIA.replace("billing", "purchasing")))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.role").value("purchasing"));
		mvc.perform(put(path).with(userOf(TENANT_A))
			.header("If-Match", "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(LUCIA))
			.andExpect(status().isPreconditionFailed());

		mvc.perform(delete(path).with(userOf(TENANT_A))).andExpect(status().isNoContent());
		mvc.perform(get(path).with(userOf(TENANT_A))).andExpect(status().isNotFound());
	}

	@Test
	void invalidContactsAre422() throws Exception {
		mvc.perform(create(company, "{\"firstName\": \"Lucía\", \"email\": \"no-es-un-correo\", \"phone\": \"x\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors", hasSize(2)));
		mvc.perform(create(company, "{\"firstName\": \"Lucía\", \"role\": \"jefe\"}")).andExpect(status().isBadRequest());
	}

	@Test
	void contactsOfACompanyOfAnotherTenantAreNotFound() throws Exception {
		mvc.perform(create(company, LUCIA)).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/companies/{id}/contacts", company).with(userOf(TENANT_B, "bruno")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("company.not_found"));
		mvc.perform(post("/api/v1/companies/{id}/contacts", company).with(userOf(TENANT_B, "bruno"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(LUCIA)).andExpect(status().isNotFound());
	}

	@Test
	void aContactIsOnlyReachedThroughItsCompany() throws Exception {
		String contact = JsonPath.read(mvc.perform(create(company, LUCIA)).andReturn().getResponse().getContentAsString(),
				"$.id");
		String other = JsonPath.read(mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Acme\",\"taxId\":\"B12345674\"}")).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/v1/companies/{c}/contacts/{id}", other, contact).with(userOf(TENANT_A)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("contact.not_found"));
	}

	@Test
	void editingContactsRequiresCompanyEdit() throws Exception {
		mvc.perform(post("/api/v1/companies/{id}/contacts", company).with(userOf(TENANT_A, "pepe", "guest"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(LUCIA)).andExpect(status().isForbidden());
	}

	private static MockHttpServletRequestBuilder create(UUID company, String body) {
		return post("/api/v1/companies/{id}/contacts", company).with(userOf(TENANT_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
