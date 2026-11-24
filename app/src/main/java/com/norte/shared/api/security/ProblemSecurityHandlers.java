package com.norte.shared.api.security;

import java.io.IOException;

import com.norte.contract.model.ProblemDto;
import com.norte.shared.api.problem.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401 y 403 de Spring Security también en Problem Details. */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final Problems problems;

	private final JsonMapper json;

	ProblemSecurityHandlers(Problems problems, JsonMapper json) {
		this.problems = problems;
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(response, problems.problem(HttpStatus.UNAUTHORIZED, "auth.unauthenticated",
				"Necesitas iniciar sesión", null));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, problems.problem(HttpStatus.FORBIDDEN, "auth.forbidden",
				"No tienes permiso para esta acción", null));
	}

	void write(HttpServletResponse response, ProblemDto problem) throws IOException {
		response.setStatus(problem.getStatus());
		response.setContentType(Problems.PROBLEM_JSON.toString());
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), problem);
	}

}
