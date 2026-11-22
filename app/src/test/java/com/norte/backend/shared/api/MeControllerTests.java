package com.norte.backend.shared.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
class MeControllerTests {

	@Autowired
	MockMvc mvc;

	@Test
	void returnsCurrentUserFromToken() throws Exception {
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject("user-1")
			.claim("preferred_username", "ana")
			.claim("tenant_id", "11111111-1111-1111-1111-111111111111")
			.claim("realm_access", Map.of("roles", List.of("user", "admin"))))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("user-1"))
			.andExpect(jsonPath("$.username").value("ana"))
			.andExpect(jsonPath("$.tenantId").value("11111111-1111-1111-1111-111111111111"))
			.andExpect(jsonPath("$.roles[0]").value("admin"));
	}

	@Test
	void rejectsAnonymousRequests() throws Exception {
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

}
