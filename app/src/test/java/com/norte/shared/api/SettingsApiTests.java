package com.norte.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.jayway.jsonpath.JsonPath;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Ajustes de la empresa del tenant (paso 1.9). */
class SettingsApiTests extends IntegrationTest {

	private static final String SETTINGS = "/api/v1/settings/company";

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D',
			'R' };

	private static final String ACME = """
			{
			  "legalName": "  Acme Ibérica S.L. ",
			  "tradeName": "Acme",
			  "taxId": "b-12345674",
			  "address": {"line1": "Calle Mayor 1", "postalCode": "46001", "city": "Valencia", "country": "ES"},
			  "email": "Facturas@ACME.test",
			  "phone": "+34 961 000 000",
			  "website": "https://acme.test",
			  "fiscalYearStartMonth": 7,
			  "timeZone": "Atlantic/Canary",
			  "language": "en"
			}""";

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void theTenantStartsWithItsNameAndTheDefaults() throws Exception {
		mvc.perform(get(SETTINGS).with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"0\""))
			.andExpect(jsonPath("$.legalName").value("Tenant A"))
			.andExpect(jsonPath("$.address.country").value("ES"))
			.andExpect(jsonPath("$.fiscalYearStartMonth").value(1))
			.andExpect(jsonPath("$.timeZone").value("Europe/Madrid"))
			.andExpect(jsonPath("$.language").value("es"))
			.andExpect(jsonPath("$.taxId").doesNotExist())
			.andExpect(jsonPath("$.logoUrl").doesNotExist());
		mvc.perform(get(SETTINGS).with(userOf(TENANT_B, "bruno"))).andExpect(jsonPath("$.legalName").value("Tenant B"));
	}

	@Test
	void anAdminUpdatesTheSettingsWhichAreNormalized() throws Exception {
		mvc.perform(update("\"0\"", ACME).with(admin()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.legalName").value("Acme Ibérica S.L."))
			.andExpect(jsonPath("$.taxId").value("B12345674"))
			.andExpect(jsonPath("$.email").value("Facturas@acme.test"))
			.andExpect(jsonPath("$.address.city").value("Valencia"))
			.andExpect(jsonPath("$.address.province").doesNotExist())
			.andExpect(jsonPath("$.fiscalYearStartMonth").value(7))
			.andExpect(jsonPath("$.timeZone").value("Atlantic/Canary"))
			.andExpect(jsonPath("$.language").value("en"));

		mvc.perform(get(SETTINGS).with(userOf(TENANT_B, "bruno"))).andExpect(jsonPath("$.legalName").value("Tenant B"));
	}

	@Test
	void updatingAnOldVersionIs412() throws Exception {
		mvc.perform(update("\"0\"", ACME).with(admin())).andExpect(status().isOk());

		mvc.perform(update("\"0\"", ACME).with(admin()))
			.andExpect(status().isPreconditionFailed())
			.andExpect(jsonPath("$.code").value("concurrency.precondition_failed"));
		mvc.perform(update("*", ACME).with(admin())).andExpect(status().isPreconditionFailed());
	}

	@Test
	void allTheValidationErrorsComeTogether() throws Exception {
		String invalid = """
				{
				  "legalName": " ",
				  "taxId": "B12345675",
				  "address": {"postalCode": "99999", "country": "ES"},
				  "email": "no-es-un-correo",
				  "phone": "llámame",
				  "website": "no es una web",
				  "fiscalYearStartMonth": 1,
				  "timeZone": "Mars/Olympus",
				  "language": "es"
				}""";

		mvc.perform(update("\"0\"", invalid).with(admin()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("legalName", "taxId",
					"address.postalCode", "email", "phone", "website", "timeZone")));
	}

	@Test
	void aPlainUserReadsButDoesNotEdit() throws Exception {
		mvc.perform(get(SETTINGS).with(userOf(TENANT_A))).andExpect(status().isOk());
		mvc.perform(update("\"0\"", ACME).with(userOf(TENANT_A)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("auth.forbidden"));
	}

	@Test
	void theLogoIsStoredAndShownWithASignedUrl() throws Exception {
		MvcResult uploaded = mvc.perform(logo("\"0\"", PNG).with(admin()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.logoUrl").exists())
			.andReturn();
		URI logoUrl = URI.create(JsonPath.read(uploaded.getResponse().getContentAsString(), "$.logoUrl"));

		HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(logoUrl).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(image.statusCode()).isEqualTo(200);
		assertThat(image.body()).isEqualTo(PNG);
		assertThat(image.headers().firstValue("Content-Type")).hasValue("image/png");

		mvc.perform(delete(SETTINGS + "/logo").header("If-Match", "\"1\"").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logoUrl").doesNotExist());
		// El fichero se borra con el logo.
		assertThat(http.send(HttpRequest.newBuilder(logoUrl).build(), HttpResponse.BodyHandlers.discarding())
			.statusCode()).isEqualTo(404);
	}

	@Test
	void aLogoThatIsNotAnImageIs422EvenIfItSaysSo() throws Exception {
		byte[] script = "<svg onload=alert(1)>".getBytes();

		mvc.perform(logo("\"0\"", script).with(admin()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("file"))
			.andExpect(jsonPath("$.errors[0].code").value("logo.unsupported_type"));
	}

	@Test
	void theHistoryListsTheChangedFields() throws Exception {
		mvc.perform(update("\"0\"", ACME).with(admin())).andExpect(status().isOk());

		mvc.perform(get(SETTINGS + "/history").with(userOf(TENANT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].action").value("updated"))
			.andExpect(jsonPath("$.items[0].actor.name").value("ana"))
			.andExpect(jsonPath("$.items[0].changes[0].field").value("legalName"))
			.andExpect(jsonPath("$.items[0].changes[0].from").value("Tenant A"))
			.andExpect(jsonPath("$.items[0].changes[0].to").value("Acme Ibérica S.L."))
			.andExpect(jsonPath("$.items[0].changes[*].field").value(not(containsInAnyOrder("logo"))));
		mvc.perform(get(SETTINGS + "/history").with(userOf(TENANT_B, "bruno")))
			.andExpect(jsonPath("$.items[0].action").value("created"));
	}

	private static RequestPostProcessor admin() {
		return userOf(TENANT_A, "ana", "admin");
	}

	private static MockHttpServletRequestBuilder update(String ifMatch, String body) {
		return put(SETTINGS).header("If-Match", ifMatch).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockMultipartHttpServletRequestBuilder logo(String ifMatch, byte[] content) {
		return multipart(HttpMethod.PUT, SETTINGS + "/logo")
			.file(new MockMultipartFile("file", "logo.png", "image/png", content))
			.header("If-Match", ifMatch);
	}

}
