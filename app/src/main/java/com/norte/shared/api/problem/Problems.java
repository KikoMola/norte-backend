package com.norte.shared.api.problem;

import java.util.List;

import com.norte.contract.model.ProblemDto;
import com.norte.contract.model.ValidationErrorDto;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Construye respuestas Problem Details (RFC 9457) con el esquema {@code Problem} del contrato: {@code type},
 * {@code title}, {@code status}, {@code code}, {@code traceId} y {@code errors[]}.
 */
@Component
public class Problems {

	public static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

	private static final String TYPE_BASE = "https://norte.dev/problems/";

	private final ObjectProvider<Tracer> tracer;

	public Problems(ObjectProvider<Tracer> tracer) {
		this.tracer = tracer;
	}

	public ProblemDto problem(HttpStatusCode status, String code, String title, String detail) {
		var problem = new ProblemDto(TYPE_BASE + slug(code), title, status.value(), code);
		problem.setDetail(detail);
		problem.setTraceId(currentTraceId());
		return problem;
	}

	public ProblemDto validation(String title, List<ValidationErrorDto> errors) {
		var problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "validation", title, null);
		problem.setErrors(errors);
		return problem;
	}

	public ResponseEntity<ProblemDto> response(ProblemDto problem) {
		return ResponseEntity.status(problem.getStatus()).contentType(PROBLEM_JSON).body(problem);
	}

	private String currentTraceId() {
		Tracer current = tracer.getIfAvailable();
		if (current != null) {
			Span span = current.currentSpan();
			if (span != null) {
				return span.context().traceId();
			}
		}
		return MDC.get("traceId");
	}

	/** {@code company.tax_id.duplicate} → {@code company-tax-id-duplicate}. */
	static String slug(String code) {
		return code.replace('.', '-').replace('_', '-');
	}

}
