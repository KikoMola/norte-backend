package com.norte.shared.api;

import com.norte.shared.api.problem.PreconditionFailedException;

/**
 * ETag de los recursos modificables (paso 1.6): la versión de {@code @Version} entre comillas ({@code "3"}). Las
 * actualizaciones exigen {@code If-Match} con ese valor y responden 412 si el recurso ha cambiado desde que se leyó.
 * Si dos actualizaciones pasan la comprobación a la vez, el {@code @Version} de JPA hace que la segunda falle con 409.
 */
public final class ETags {

	private ETags() {
	}

	public static String of(long version) {
		return "\"" + version + "\"";
	}

	/** Acepta la ETag con o sin comillas y la forma débil {@code W/"3"}. */
	public static void requireMatch(String ifMatch, long currentVersion) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new PreconditionFailedException("Falta If-Match con la versión que se quiere modificar.");
		}
		String value = ifMatch.strip();
		if (value.startsWith("W/")) {
			value = value.substring(2);
		}
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			value = value.substring(1, value.length() - 1);
		}
		if (!value.equals(String.valueOf(currentVersion))) {
			throw new PreconditionFailedException("El recurso ha cambiado desde que lo leíste.");
		}
	}

}
