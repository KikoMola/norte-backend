package com.norte.shared.api.security;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.api.problem.Problems;
import com.norte.shared.application.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige un {@code tenant_id} válido (UUID) en todo token autenticado y lo deja en el MDC como {@code tenantId}, para
 * que salga en los logs JSON junto al {@code traceId} (pasos 0.15 y 0.18).
 */
@Component
class TenantFilter extends OncePerRequestFilter {

	static final String MDC_TENANT = "tenantId";

	private final Problems problems;

	private final ProblemSecurityHandlers handlers;

	TenantFilter(Problems problems, ProblemSecurityHandlers handlers) {
		this.problems = problems;
		this.handlers = handlers;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken token)) {
			chain.doFilter(request, response);
			return;
		}
		Optional<UUID> tenantId = TenantContext.tenantIdOf(token.getToken());
		if (tenantId.isEmpty()) {
			SecurityContextHolder.clearContext();
			handlers.write(response, problems.problem(HttpStatus.UNAUTHORIZED, "auth.tenant_missing",
					"La sesión no tiene un tenant válido", "El token no incluye un claim tenant_id con un UUID."));
			return;
		}
		MDC.put(MDC_TENANT, tenantId.get().toString());
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_TENANT);
		}
	}

}
