package com.norte.shared.api.security;

import java.util.Collection;
import java.util.stream.Collectors;

import com.norte.shared.api.idempotency.IdempotencyFilter;
import com.norte.shared.application.TenantContext;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
// Solo con servidor web: los comandos de administración (provision-tenant) arrancan sin él.
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ProblemSecurityHandlers handlers, TenantFilter tenantFilter,
			IdempotencyFilter idempotencyFilter) throws Exception {
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
			.addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
			// Después del tenant: las claves de idempotencia son por tenant (paso 1.6).
			.addFilterAfter(idempotencyFilter, TenantFilter.class);
		return http.build();
	}

	/** Los filtros solo deben correr dentro de la cadena de seguridad, no además como filtros de servlet. */
	@Bean
	FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
		var registration = new FilterRegistrationBean<>(tenantFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter idempotencyFilter) {
		var registration = new FilterRegistrationBean<>(idempotencyFilter);
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
