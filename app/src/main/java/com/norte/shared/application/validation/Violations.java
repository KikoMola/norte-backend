package com.norte.shared.application.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.norte.shared.domain.Email;
import com.norte.shared.domain.TaxId;
import com.norte.shared.domain.error.ValidationException;
import com.norte.shared.domain.error.ValidationException.Violation;

/**
 * Reglas de validación comunes (pasos 1.9 y 1.10) que se acumulan para devolverlas todas en un solo 422. Cada método
 * devuelve el valor normalizado (sin espacios en los extremos, vacío como {@code null}, NIF en mayúsculas…) y anota
 * la infracción si la hay.
 *
 * <pre>
 * var check = new Violations();
 * String name = check.required("name", body.name(), 200);
 * String email = check.email("email", body.email());
 * check.throwIfAny();
 * </pre>
 */
public final class Violations {

	private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

	private static final Pattern SPANISH_POSTAL_CODE = Pattern.compile("(0[1-9]|[1-4]\\d|5[0-2])\\d{3}");

	private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ()-]{6,30}");

	private static final Pattern WEBSITE = Pattern.compile("(https?://)?[\\p{L}0-9.-]+\\.[\\p{L}]{2,}(/\\S*)?",
			Pattern.CASE_INSENSITIVE);

	private final List<Violation> violations = new ArrayList<>();

	public void add(String field, String code, String message) {
		violations.add(new Violation(field, code, message));
	}

	/** Texto obligatorio de como mucho {@code max} caracteres. */
	public String required(String field, String value, int max) {
		String stripped = strip(value);
		if (stripped == null) {
			add(field, "required", "Este campo es obligatorio.");
			return null;
		}
		return maxLength(field, stripped, max);
	}

	/** Texto opcional: vacío es {@code null}. */
	public String optional(String field, String value, int max) {
		return maxLength(field, strip(value), max);
	}

	/** NIF, NIE o CIF normalizado ({@code B12345674}). */
	public String taxId(String field, String value, boolean required) {
		String stripped = strip(value);
		if (stripped == null) {
			if (required) {
				add(field, "required", "El NIF es obligatorio.");
			}
			return null;
		}
		if (!TaxId.isValid(stripped)) {
			add(field, "tax_id.invalid", "El NIF no es válido.");
			return stripped;
		}
		return TaxId.of(stripped).value();
	}

	public String email(String field, String value) {
		String stripped = strip(value);
		if (stripped == null) {
			return null;
		}
		if (!Email.isValid(stripped)) {
			add(field, "email.invalid", "El correo no es válido.");
			return stripped;
		}
		return Email.of(stripped).value();
	}

	public String phone(String field, String value) {
		String stripped = strip(value);
		if (stripped != null && !PHONE.matcher(stripped).matches()) {
			add(field, "phone.invalid", "El teléfono no es válido.");
		}
		return stripped;
	}

	public String website(String field, String value) {
		String stripped = strip(value);
		if (stripped != null && (stripped.length() > 200 || !WEBSITE.matcher(stripped).matches())) {
			add(field, "website.invalid", "La web no es válida.");
		}
		return stripped;
	}

	/** Código ISO 3166-1 alfa-2, obligatorio. */
	public String country(String field, String value) {
		String stripped = strip(value);
		if (stripped == null || !COUNTRY.matcher(stripped).matches()) {
			add(field, "country.invalid", "El país no es válido.");
		}
		return stripped;
	}

	/** Opcional; si el país es España, 5 cifras de una provincia que existe (01 a 52). */
	public String postalCode(String field, String value, String country) {
		String stripped = maxLength(field, strip(value), 20);
		if (stripped != null && "ES".equals(country) && !SPANISH_POSTAL_CODE.matcher(stripped).matches()) {
			add(field, "postal_code.invalid", "El código postal español tiene 5 cifras y empieza por 01 a 52.");
		}
		return stripped;
	}

	/** Importe opcional, sin negativos y con como mucho 4 decimales y 15 cifras enteras, como {@code Money}. */
	public BigDecimal amount(String field, BigDecimal value) {
		if (value == null) {
			return null;
		}
		if (value.signum() < 0) {
			add(field, "min", "No puede ser negativo.");
		}
		else if (value.stripTrailingZeros().scale() > 4 || value.precision() - value.scale() > 15) {
			add(field, "amount.invalid", "El importe no es válido.");
		}
		return value;
	}

	public boolean isEmpty() {
		return violations.isEmpty();
	}

	/** @throws ValidationException con todas las infracciones anotadas, si hay alguna */
	public void throwIfAny() {
		if (!violations.isEmpty()) {
			throw new ValidationException(violations);
		}
	}

	private String maxLength(String field, String value, int max) {
		if (value != null && value.length() > max) {
			add(field, "too_long", "Como mucho " + max + " caracteres.");
		}
		return value;
	}

	private static String strip(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
