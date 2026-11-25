package com.norte.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code Idempotency-Key} en los POST (paso 1.6), con el alta de empresa como ejemplo. */
class IdempotencyApiTests extends IntegrationTest {

	private static final String ACME = "{\"name\":\"Acme Ibérica S.L.\",\"taxId\":\"B12345674\"}";

	@Test
	void repeatingTheKeyReplaysTheResponseWithoutRepeatingTheEffect() throws Exception {
		UUID key = UUID.randomUUID();

		MvcResult first = mvc.perform(create(key, ACME).with(userOf(TENANT_A)))
			.andExpect(status().isCreated())
			.andExpect(header().doesNotExist("Idempotency-Replayed"))
			.andReturn();
		MvcResult second = mvc.perform(create(key, ACME).with(userOf(TENANT_A)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(header().string("Location", first.getResponse().getHeader("Location")))
			.andExpect(header().string("ETag", first.getResponse().getHeader("ETag")))
			.andReturn();

		assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		assertThat(companiesOf(TENANT_A)).isEqualTo(1);
	}

	@Test
	void errorsOfTheClientAreAlsoReplayed() throws Exception {
		UUID key = UUID.randomUUID();
		String invalid = "{\"name\":\"Acme\",\"taxId\":\"B12345675\"}";

		mvc.perform(create(key, invalid).with(userOf(TENANT_A))).andExpect(status().isUnprocessableContent());
		mvc.perform(create(key, invalid).with(userOf(TENANT_A)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.errors[0].code").value("tax_id.invalid"));
	}

	@Test
	void sameKeyWithAnotherRequestIs422() throws Exception {
		UUID key = UUID.randomUUID();
		mvc.perform(create(key, ACME).with(userOf(TENANT_A))).andExpect(status().isCreated());

		mvc.perform(create(key, "{\"name\":\"Globex\",\"taxId\":\"A58818501\"}").with(userOf(TENANT_A)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("idempotency.key_reused"));
		assertThat(companiesOf(TENANT_A)).isEqualTo(1);
	}

	@Test
	void keysArePerTenant() throws Exception {
		UUID key = UUID.randomUUID();
		mvc.perform(create(key, ACME).with(userOf(TENANT_A))).andExpect(status().isCreated());
		mvc.perform(create(key, ACME).with(userOf(TENANT_B)))
			.andExpect(status().isCreated())
			.andExpect(header().doesNotExist("Idempotency-Replayed"));

		assertThat(companiesOf(TENANT_A)).isEqualTo(1);
		assertThat(companiesOf(TENANT_B)).isEqualTo(1);
	}

	@Test
	void keyInProgressIs409() throws Exception {
		UUID key = UUID.randomUUID();
		// Simula una primera petición que aún no ha terminado (misma huella que la que llega ahora).
		mvc.perform(create(key, ACME).with(userOf(TENANT_A))).andExpect(status().isCreated());
		database.asTenant(TENANT_A)
			.update("update shared.idempotency_key set completed_at = null, created_at = now() where key = ?", key);

		mvc.perform(create(key, ACME).with(userOf(TENANT_A)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("idempotency.in_progress"));
	}

	@Test
	void invalidKeyIs400() throws Exception {
		mvc.perform(post("/api/v1/companies").with(userOf(TENANT_A))
			.header("Idempotency-Key", "no-es-un-uuid")
			.contentType(MediaType.APPLICATION_JSON)
			.content(ACME))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("request.invalid_parameter"));
	}

	private static MockHttpServletRequestBuilder create(UUID key, String body) {
		return post("/api/v1/companies").header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private int companiesOf(UUID tenantId) {
		return database.asTenant(tenantId).queryForObject("select count(*) from parties.company", Integer.class);
	}

}
