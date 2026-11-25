package com.norte.shared.api.problem;

/** El {@code If-Match} no coincide con la versión actual del recurso: 412 {@code concurrency.precondition_failed}. */
public class PreconditionFailedException extends RuntimeException {

	public PreconditionFailedException(String message) {
		super(message);
	}

}
