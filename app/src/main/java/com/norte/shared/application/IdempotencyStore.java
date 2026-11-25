package com.norte.shared.application;

import java.util.UUID;

/**
 * Claves de idempotencia del tenant actual (paso 1.6). Cada operación va en su propia transacción corta, aparte de la
 * de la petición.
 */
public interface IdempotencyStore {

	/**
	 * Reserva la clave para esta petición. Una clave de hace más de 24 horas, o abandonada en curso más de un minuto,
	 * se considera libre.
	 */
	Claim claim(UUID key, String requestHash);

	/** Guarda la respuesta de una clave reservada. */
	void complete(UUID key, StoredResponse response);

	/** Libera una clave reservada cuya petición falló sin respuesta guardable (5xx o excepción). */
	void release(UUID key);

	sealed interface Claim {

		/** La clave era nueva: hay que ejecutar la petición. */
		record Acquired() implements Claim {
		}

		/** Ya hay respuesta para la misma petición: se devuelve tal cual. */
		record Replay(StoredResponse response) implements Claim {
		}

		/** La clave se usó con otra petición. */
		record Mismatch() implements Claim {
		}

		/** La petición original aún no ha terminado. */
		record InProgress() implements Claim {
		}

	}

	record StoredResponse(int status, String contentType, String location, String etag, byte[] body) {
	}

}
