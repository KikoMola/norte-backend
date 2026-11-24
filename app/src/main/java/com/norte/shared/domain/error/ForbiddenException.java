package com.norte.shared.domain.error;

/** El usuario está autenticado pero no puede hacer esta acción (403). */
public final class ForbiddenException extends DomainException {

	public ForbiddenException(String code, String message) {
		super(code, message);
	}

}
