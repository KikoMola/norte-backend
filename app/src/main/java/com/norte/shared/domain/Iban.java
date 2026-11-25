package com.norte.shared.domain;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Código IBAN (ISO 13616). Se valida la longitud del país, los dígitos de control (módulo 97) y, en las cuentas
 * españolas, los dos dígitos de control del antiguo CCC. Se guarda sin espacios y en mayúsculas.
 */
public record Iban(String value) {

	private static final Pattern SHAPE = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

	private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

	private static final int[] CCC_WEIGHTS = { 1, 2, 4, 8, 5, 10, 9, 7, 3, 6 };

	/** Longitud del IBAN por país, según el registro de SWIFT. */
	private static final Map<String, Integer> LENGTHS = Map.ofEntries(Map.entry("AD", 24), Map.entry("AE", 23),
			Map.entry("AL", 28), Map.entry("AT", 20), Map.entry("AZ", 28), Map.entry("BA", 20), Map.entry("BE", 16),
			Map.entry("BG", 22), Map.entry("BH", 22), Map.entry("BR", 29), Map.entry("CH", 21), Map.entry("CR", 22),
			Map.entry("CY", 28), Map.entry("CZ", 24), Map.entry("DE", 22), Map.entry("DK", 18), Map.entry("DO", 28),
			Map.entry("EE", 20), Map.entry("EG", 29), Map.entry("ES", 24), Map.entry("FI", 18), Map.entry("FO", 18),
			Map.entry("FR", 27), Map.entry("GB", 22), Map.entry("GE", 22), Map.entry("GI", 23), Map.entry("GL", 18),
			Map.entry("GR", 27), Map.entry("GT", 28), Map.entry("HR", 21), Map.entry("HU", 28), Map.entry("IE", 22),
			Map.entry("IL", 23), Map.entry("IS", 26), Map.entry("IT", 27), Map.entry("JO", 30), Map.entry("KW", 30),
			Map.entry("KZ", 20), Map.entry("LB", 28), Map.entry("LI", 21), Map.entry("LT", 20), Map.entry("LU", 20),
			Map.entry("LV", 21), Map.entry("MC", 27), Map.entry("MD", 24), Map.entry("ME", 22), Map.entry("MK", 19),
			Map.entry("MR", 27), Map.entry("MT", 31), Map.entry("MU", 30), Map.entry("NL", 18), Map.entry("NO", 15),
			Map.entry("PK", 24), Map.entry("PL", 28), Map.entry("PS", 29), Map.entry("PT", 25), Map.entry("QA", 29),
			Map.entry("RO", 24), Map.entry("RS", 22), Map.entry("SA", 24), Map.entry("SE", 24), Map.entry("SI", 19),
			Map.entry("SK", 24), Map.entry("SM", 27), Map.entry("TN", 24), Map.entry("TR", 26), Map.entry("UA", 29),
			Map.entry("VA", 22), Map.entry("VG", 24), Map.entry("XK", 20));

	public Iban {
		Objects.requireNonNull(value, "value");
		if (!isValidNormalized(value)) {
			throw new InvalidIbanException(value);
		}
	}

	public static Iban of(String raw) {
		return new Iban(normalize(raw));
	}

	public static boolean isValid(String raw) {
		return raw != null && isValidNormalized(normalize(raw));
	}

	static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
	}

	public String countryCode() {
		return value.substring(0, 2);
	}

	/** En grupos de cuatro, como se imprime en una factura: {@code ES91 2100 0418 4502 0005 1332}. */
	public String formatted() {
		return value.replaceAll("(.{4})(?!$)", "$1 ");
	}

	@Override
	public String toString() {
		return value;
	}

	private static boolean isValidNormalized(String value) {
		if (!SHAPE.matcher(value).matches()) {
			return false;
		}
		Integer length = LENGTHS.get(value.substring(0, 2));
		if (length == null || length != value.length()) {
			return false;
		}
		if (mod97(value.substring(4) + value.substring(0, 4)) != 1) {
			return false;
		}
		return !value.startsWith("ES") || hasValidCccControlDigits(value.substring(4));
	}

	/** Letras a números (A = 10 … Z = 35) y resto de la división entre 97. */
	static int mod97(String rearranged) {
		var digits = new StringBuilder(rearranged.length() * 2);
		for (char c : rearranged.toCharArray()) {
			digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
		}
		return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue();
	}

	/** CCC: entidad (4), oficina (4), control (2) y cuenta (10). */
	private static boolean hasValidCccControlDigits(String ccc) {
		if (!ccc.chars().allMatch(Character::isDigit)) {
			return false;
		}
		String expected = "" + cccControlDigit("00" + ccc.substring(0, 8)) + cccControlDigit(ccc.substring(10));
		return expected.equals(ccc.substring(8, 10));
	}

	static int cccControlDigit(String tenDigits) {
		int sum = 0;
		for (int i = 0; i < 10; i++) {
			sum += (tenDigits.charAt(i) - '0') * CCC_WEIGHTS[i];
		}
		int digit = 11 - sum % 11;
		return digit == 11 ? 0 : digit == 10 ? 1 : digit;
	}

	public static final class InvalidIbanException extends IllegalArgumentException {

		InvalidIbanException(String value) {
			super("Invalid IBAN: " + value);
		}

	}

}
