package com.norte.shared.domain.error;

/** La operación choca con el estado actual, por ejemplo un NIF duplicado (409). */
public final class ConflictException extends DomainException {

	public ConflictException(String code, String message) {
		super(code, message);
	}

}
