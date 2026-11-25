package com.norte.shared.domain.error;

/**
 * La versión que el cliente quería modificar ya no es la actual: otro usuario lo cambió desde que lo leyó (412
 * {@code concurrency.precondition_failed}). Se lanza dentro de la transacción que modifica.
 */
public final class StaleVersionException extends DomainException {

	public static final String CODE = "concurrency.precondition_failed";

	public StaleVersionException() {
		super(CODE, "Otro usuario ha modificado este registro");
	}

	/** @throws StaleVersionException si las versiones no coinciden */
	public static void check(long expected, long current) {
		if (expected != current) {
			throw new StaleVersionException();
		}
	}

}
