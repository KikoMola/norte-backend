package com.norte.shared.application.files;

import java.util.regex.Pattern;

/**
 * Ruta de un fichero dentro del espacio del tenant (paso 1.7), sin el prefijo del tenant: {@code invoices/2026/
 * F-2026-0001.pdf}. El almacén le antepone siempre el tenant actual, así que una ruta nunca alcanza ficheros de otro.
 *
 * <p>Segmentos en minúsculas, cifras, punto, guion y guion bajo, sin {@code ..} ni barras al principio o al final.
 */
public record FilePath(String value) {

	public static final int MAX_LENGTH = 512;

	private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_][a-z0-9._-]*");

	public FilePath {
		if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("File path must have between 1 and " + MAX_LENGTH + " characters");
		}
		for (String segment : value.split("/", -1)) {
			if (!SEGMENT.matcher(segment).matches() || segment.equals("..")) {
				throw new IllegalArgumentException("Invalid file path segment: '" + segment + "'");
			}
		}
	}

	public static FilePath of(String first, String... more) {
		return new FilePath(more.length == 0 ? first : first + "/" + String.join("/", more));
	}

	@Override
	public String toString() {
		return value;
	}

}
