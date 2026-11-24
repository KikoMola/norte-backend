package com.norte.shared.api.security;

import java.util.Collection;
import java.util.stream.Collectors;

import com.norte.shared.application.TenantContext;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * La API es un resource server sin estado: solo acepta un JWT de Keycloak como Bearer (lo añade el BFF). No hay
 * cookies, así que CSRF no aplica aquí; lo gestiona el BFF.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ProblemSecurityHandlers handlers, TenantFilter tenantFilter)
			throws Exception {
		http.authorizeHttpRequests(requests -> requests
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(server -> server
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				.authenticationEntryPoint(handlers)
				.accessDeniedHandler(handlers))
			.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(AbstractHttpConfigurer::disable)
			.addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	/** El filtro solo debe correr dentro de la cadena de seguridad, no además como filtro de servlet. */
	@Bean
	FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
		var registration = new FilterRegistrationBean<>(tenantFilter);
		registration.setEnabled(false);
		return registration;
	}

	/** Roles del realm ({@code realm_access.roles}) como autoridades {@code ROLE_*}. */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(realmRoles());
		converter.setPrincipalClaimName("sub");
		return converter;
	}

	private static Converter<Jwt, Collection<GrantedAuthority>> realmRoles() {
		return jwt -> TenantContext.realmRoles(jwt)
			.stream()
			.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
			.collect(Collectors.toUnmodifiableList());
	}

}
