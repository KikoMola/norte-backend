package com.norte.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Dirección de correo. La validación es deliberadamente práctica (la que hace un formulario, no la RFC 5322 completa):
 * una parte local, una arroba y un dominio con al menos un punto, sin espacios. El dominio se pasa a minúsculas; la
 * parte local se respeta porque, en teoría, distingue mayúsculas.
 */
public record Email(String value) {

	public static final int MAX_LENGTH = 254;

	private static final Pattern SHAPE = Pattern
		.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

	public Email {
		Objects.requireNonNull(value, "value");
		if (!isValidNormalized(value)) {
			throw new InvalidEmailException(value);
		}
	}

	public static Email of(String raw) {
		return new Email(normalize(raw));
	}

	public static boolean isValid(String raw) {
		return raw != null && isValidNormalized(normalize(raw));
	}

	static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String trimmed = raw.strip();
		int at = trimmed.lastIndexOf('@');
		if (at < 0) {
			return trimmed;
		}
		return trimmed.substring(0, at + 1) + trimmed.substring(at + 1).toLowerCase(Locale.ROOT);
	}

	private static boolean isValidNormalized(String value) {
		if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
			return false;
		}
		String local = value.substring(0, value.indexOf('@'));
		return !local.startsWith(".") && !local.endsWith(".") && !local.contains("..");
	}

	public String domain() {
		return value.substring(value.indexOf('@') + 1);
	}

	@Override
	public String toString() {
		return value;
	}

	public static final class InvalidEmailException extends IllegalArgumentException {

		InvalidEmailException(String value) {
			super("Invalid email: " + value);
		}

	}

}
