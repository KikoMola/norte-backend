package com.norte.shared.api;

import com.norte.shared.api.problem.PreconditionFailedException;

/**
 * ETag de los recursos modificables (paso 1.6): la versión de {@code @Version} entre comillas ({@code "3"}). Las
 * actualizaciones exigen {@code If-Match} con ese valor y responden 412 si el recurso ha cambiado desde que se leyó.
 *
 * <p>Lo seguro es comparar dentro de la transacción que modifica: el controlador extrae la versión con {@link #parse}
 * y el servicio la compara con la que acaba de cargar ({@code StaleVersionException}, también 412). Comparar fuera,
 * con {@link #requireMatch}, deja pasar un cambio que llegue entre la comprobación y la escritura.
 */
public final class ETags {

	private ETags() {
	}

	public static String of(long version) {
		return "\"" + version + "\"";
	}

	/** Acepta la ETag con o sin comillas y la forma débil {@code W/"3"}. */
	public static void requireMatch(String ifMatch, long currentVersion) {
		if (parse(ifMatch) != currentVersion) {
			throw new PreconditionFailedException("El recurso ha cambiado desde que lo leíste.");
		}
	}

	/**
	 * Versión que el cliente quiere modificar, sacada de {@code If-Match}. Sin cabecera, o con algo que no es una
	 * versión ({@code *} incluido), 412: modificar sin saber qué se pisa no se permite.
	 */
	public static long parse(String ifMatch) {
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
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new PreconditionFailedException("El recurso ha cambiado desde que lo leíste.");
		}
	}

}
