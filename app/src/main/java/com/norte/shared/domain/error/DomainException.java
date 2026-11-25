package com.norte.shared.domain.error;

/**
 * Raíz de las excepciones de dominio. Cada una lleva un {@code code} estable que viaja en el campo {@code code} de
 * Problem Details y sirve de clave de traducción en el frontend. Catálogo en {@code docs/errores.md}.
 */
public abstract sealed class DomainException extends RuntimeException
		permits NotFoundException, ConflictException, ValidationException, ForbiddenException, StaleVersionException {

	private final String code;

	protected DomainException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}

}
