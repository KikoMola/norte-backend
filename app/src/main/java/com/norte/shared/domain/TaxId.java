package com.norte.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Número de identificación fiscal español: NIF de persona física (DNI y K, L, M), NIE o NIF de persona jurídica
 * (antiguo CIF). Se valida el dígito o letra de control y se guarda normalizado: mayúsculas, sin espacios, puntos ni
 * guiones.
 */
public record TaxId(String value) {

	private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

	private static final String CIF_CONTROL_LETTERS = "JABCDEFGHI";

	private static final Pattern DNI = Pattern.compile("^[0-9]{8}[A-Z]$");

	private static final Pattern NIE = Pattern.compile("^[XYZ][0-9]{7}[A-Z]$");

	private static final Pattern KLM = Pattern.compile("^[KLM][0-9]{7}[A-Z]$");

	private static final Pattern CIF = Pattern.compile("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$");

	public TaxId {
		Objects.requireNonNull(value, "value");
		if (!isValidNormalized(value)) {
			throw new InvalidTaxIdException(value);
		}
	}

	/** Normaliza y valida. Lanza {@link InvalidTaxIdException} si no es un NIF, NIE o CIF válido. */
	public static TaxId of(String raw) {
		return new TaxId(normalize(raw));
	}

	public static boolean isValid(String raw) {
		return raw != null && isValidNormalized(normalize(raw));
	}

	static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
	}

	private static boolean isValidNormalized(String value) {
		if (DNI.matcher(value).matches()) {
			return letterFor(Integer.parseInt(value.substring(0, 8))) == value.charAt(8);
		}
		if (NIE.matcher(value).matches()) {
			int prefix = "XYZ".indexOf(value.charAt(0));
			return letterFor(Integer.parseInt(prefix + value.substring(1, 8))) == value.charAt(8);
		}
		if (KLM.matcher(value).matches()) {
			return letterFor(Integer.parseInt(value.substring(1, 8))) == value.charAt(8);
		}
		if (CIF.matcher(value).matches()) {
			return isValidCif(value);
		}
		return false;
	}

	private static char letterFor(int number) {
		return DNI_LETTERS.charAt(number % 23);
	}

	private static boolean isValidCif(String value) {
		int sum = 0;
		for (int i = 1; i <= 7; i++) {
			int digit = value.charAt(i) - '0';
			if (i % 2 == 0) {
				sum += digit;
			}
			else {
				int doubled = digit * 2;
				sum += doubled / 10 + doubled % 10;
			}
		}
		int controlDigit = (10 - sum % 10) % 10;
		char controlLetter = CIF_CONTROL_LETTERS.charAt(controlDigit);
		char type = value.charAt(0);
		char control = value.charAt(8);
		// Entidades que llevan letra de control, las que llevan dígito y las que admiten ambos.
		if ("PQRSNW".indexOf(type) >= 0) {
			return control == controlLetter;
		}
		if ("ABEH".indexOf(type) >= 0) {
			return control == (char) ('0' + controlDigit);
		}
		return control == controlLetter || control == (char) ('0' + controlDigit);
	}

	@Override
	public String toString() {
		return value;
	}

	public static final class InvalidTaxIdException extends IllegalArgumentException {

		InvalidTaxIdException(String value) {
			super("Invalid Spanish tax id: " + value);
		}

	}

}
