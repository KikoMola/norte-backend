package com.norte.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;

class SessionAndSecurityApiTests extends IntegrationTest {

	@Test
	void meReturnsUserAndTenantFromToken() throws Exception {
		mvc.perform(get("/api/v1/me").with(userOf(TENANT_A, "ana")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("user-ana"))
			.andExpect(jsonPath("$.username").value("ana"))
			.andExpect(jsonPath("$.email").value("ana@norte.test"))
			.andExpect(jsonPath("$.tenantId").value(TENANT_A.toString()))
			.andExpect(jsonPath("$.roles[0]").value("user"));
	}

	@Test
	void anonymousRequestIs401ProblemDetails() throws Exception {
		mvc.perform(get("/api/v1/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("auth.unauthenticated"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.type").value("https://norte.dev/problems/auth-unauthenticated"));
	}

	@Test
	void tokenWithoutTenantIsRejected() throws Exception {
		mvc.perform(get("/api/v1/me").with(userWithoutTenant()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("auth.tenant_missing"));
	}

	@Test
	void unknownRouteIs404ProblemDetails() throws Exception {
		mvc.perform(get("/api/v1/does-not-exist").with(userOf(TENANT_A)))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("resource.not_found"));
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
