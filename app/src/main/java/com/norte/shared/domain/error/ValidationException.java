package com.norte.shared.domain.error;

import java.util.List;

/** Una o varias reglas de negocio incumplidas (422). Cada infracción va en {@code errors[]} de Problem Details. */
public final class ValidationException extends DomainException {

	public static final String CODE = "validation";

	private final List<Violation> violations;

	public ValidationException(List<Violation> violations) {
		super(CODE, "Datos no válidos");
		if (violations.isEmpty()) {
			throw new IllegalArgumentException("A validation exception needs at least one violation");
		}
		this.violations = List.copyOf(violations);
	}

	public static ValidationException of(String field, String code, String message) {
		return new ValidationException(List.of(new Violation(field, code, message)));
	}

	public List<Violation> violations() {
		return violations;
	}

	/**
	 * @param field ruta del campo en el cuerpo de la petición ({@code taxId}, {@code lines[2].quantity})
	 * @param code código estable ({@code required}, {@code tax_id.invalid})
	 * @param message mensaje por defecto en español
	 */
	public record Violation(String field, String code, String message) {
	}

}
