package com.norte.bff;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BffSecurityTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	ClientRegistrationRepository clients;

	@Test
	void apiWithoutSessionReturns401InsteadOfRedirect() throws Exception {
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void loginStartsAuthorizationCodeFlowWithPkce() throws Exception {
		mvc.perform(get("/oauth2/authorization/keycloak"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location",
					allOf(startsWith("http://keycloak.test/auth"), containsString("code_challenge_method=S256"))));
	}

	@Test
	void logoutWithoutCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/logout").with(oidcLogin())).andExpect(status().isForbidden());
	}

	@Test
	void logoutAnswers202WithKeycloakEndSessionUrl() throws Exception {
		var keycloak = clients.findByRegistrationId("keycloak");
		mvc.perform(post("/logout").with(oidcLogin().clientRegistration(keycloak)).with(csrf()))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", startsWith("http://keycloak.test/logout")));
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	/** Evita depender de Keycloak en los tests: el proveedor se declara a mano. */
	@TestConfiguration(proxyBeanMethods = false)
	static class Clients {

		@Bean
		ClientRegistrationRepository clientRegistrationRepository() {
			return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("keycloak")
				.clientId("norte-bff")
				.clientSecret("secret")
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid", "profile", "email")
				.authorizationUri("http://keycloak.test/auth")
				.tokenUri("http://keycloak.test/token")
				.jwkSetUri("http://keycloak.test/certs")
				.userInfoUri("http://keycloak.test/userinfo")
				.userNameAttributeName("sub")
				.providerConfigurationMetadata(Map.of("end_session_endpoint", "http://keycloak.test/logout"))
				.build());
		}

	}

}
