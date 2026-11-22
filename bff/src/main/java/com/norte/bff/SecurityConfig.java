package com.norte.bff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clients,
			BffProperties properties) throws Exception {
		http.authorizeHttpRequests(requests -> requests
				.requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
				.anyRequest().authenticated())
			.oauth2Login(login -> login
				.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(pkceResolver(clients)))
				.defaultSuccessUrl(properties.postLoginRedirect(), true))
			.logout(logout -> logout.logoutSuccessHandler(spaLogoutHandler(clients, properties)))
			// Token CSRF en la cookie XSRF-TOKEN (legible por Angular) y de vuelta en la cabecera X-XSRF-TOKEN.
			.csrf(csrf -> csrf.spa())
			// La SPA recibe 401 en /api en vez de una redirección al login que no puede seguir.
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
		return http.build();
	}

	/** Gestiona el access token de la sesión y lo renueva con el refresh token cuando caduca. */
	@Bean
	OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clients,
			OAuth2AuthorizedClientRepository authorizedClients) {
		var manager = new DefaultOAuth2AuthorizedClientManager(clients, authorizedClients);
		manager.setAuthorizedClientProvider(
				OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build());
		return manager;
	}

	private static DefaultOAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clients) {
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients,
				OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		return resolver;
	}

	/**
	 * La SPA llama a POST /logout por XHR, así que no puede seguir una redirección a Keycloak. Se responde 202 con la
	 * URL de cierre de sesión de Keycloak en Location y Angular navega a ella.
	 */
	private static OidcClientInitiatedLogoutSuccessHandler spaLogoutHandler(ClientRegistrationRepository clients,
			BffProperties properties) {
		var handler = new OidcClientInitiatedLogoutSuccessHandler(clients);
		handler.setPostLogoutRedirectUri(properties.postLogoutRedirect());
		handler.setRedirectStrategy((request, response, url) -> {
			response.setStatus(HttpStatus.ACCEPTED.value());
			response.setHeader(HttpHeaders.LOCATION, url);
		});
		return handler;
	}

}
