package com.norte.shared.domain.error;

/** El recurso no existe o pertenece a otro tenant (404; nunca 403, para no revelar que existe). */
public final class NotFoundException extends DomainException {

	public NotFoundException(String code, String message) {
		super(code, message);
	}

}
