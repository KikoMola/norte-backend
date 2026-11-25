package com.norte.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Propiedades de los identificadores validados del shared kernel (paso 1.5): {@link TaxId}, {@link Iban} y
 * {@link Email}. Los valores válidos se generan calculando sus dígitos de control, así que si el algoritmo de
 * validación y el de generación discrepan, el test lo encuentra.
 */
class IdentifierProperties {

	private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

	@Property
	void generatedDnisAndNiesAreValid(@ForAll("dnis") String dni, @ForAll("nies") String nie) {
		assertThat(TaxId.isValid(dni)).isTrue();
		assertThat(TaxId.isValid(nie)).isTrue();
	}

	@Property
	void anyOtherControlLetterIsRejected(@ForAll("dnis") String dni, @ForAll @IntRange(min = 1, max = 22) int shift) {
		char letter = dni.charAt(8);
		char wrong = DNI_LETTERS.charAt((DNI_LETTERS.indexOf(letter) + shift) % DNI_LETTERS.length());
		assertThat(TaxId.isValid(dni.substring(0, 8) + wrong)).isFalse();
	}

	@Property
	void taxIdNormalizationIsIdempotent(@ForAll("dnis") String dni) {
		String messy = " " + dni.substring(0, 2) + "." + dni.substring(2, 5) + "-" + dni.substring(5).toLowerCase();
		TaxId once = TaxId.of(messy);
		assertThat(TaxId.of(once.value())).isEqualTo(once);
		assertThat(once.value()).isEqualTo(dni);
	}

	@Property
	void generatedSpanishIbansAreValid(@ForAll("spanishIbans") String iban) {
		assertThat(Iban.isValid(iban)).isTrue();
		assertThat(Iban.of(Iban.of(iban).formatted()).value()).isEqualTo(iban);
	}

	@Property
	void changingOneDigitOfAnIbanIsDetected(@ForAll("spanishIbans") String iban,
			@ForAll @IntRange(min = 2, max = 23) int position, @ForAll @IntRange(min = 1, max = 9) int delta) {
		// El módulo 97 detecta cualquier error en un solo dígito.
		char digit = iban.charAt(position);
		char changed = (char) ('0' + (digit - '0' + delta) % 10);
		String typo = iban.substring(0, position) + changed + iban.substring(position + 1);
		assertThat(Iban.isValid(typo)).isFalse();
	}

	@Property
	void swappingTwoDifferentAdjacentDigitsIsDetected(@ForAll("spanishIbans") String iban,
			@ForAll @IntRange(min = 4, max = 22) int position) {
		char a = iban.charAt(position);
		char b = iban.charAt(position + 1);
		if (a == b) {
			return;
		}
		String swapped = iban.substring(0, position) + b + a + iban.substring(position + 2);
		assertThat(Iban.isValid(swapped)).isFalse();
	}

	@Property
	void emailDomainIsLowercasedAndNormalizationIsIdempotent(@ForAll("emails") String email) {
		Email parsed = Email.of("  " + email.substring(0, email.indexOf('@') + 1)
				+ email.substring(email.indexOf('@') + 1).toUpperCase() + " ");
		assertThat(parsed.value()).isEqualTo(email);
		assertThat(Email.of(parsed.value())).isEqualTo(parsed);
	}

	@Example
	void knownIbans() {
		assertThat(Iban.isValid("ES91 2100 0418 4502 0005 1332")).isTrue();
		assertThat(Iban.of("es9121000418450200051332").formatted()).isEqualTo("ES91 2100 0418 4502 0005 1332");
		assertThat(Iban.isValid("DE89 3704 0044 0532 0130 00")).isTrue();
		assertThat(Iban.isValid("GB29 NWBK 6016 1331 9268 19")).isTrue();
		// Módulo 97 correcto pero dígitos de control del CCC incorrectos.
		assertThat(Iban.isValid("ES0421000418411234567891")).isFalse();
		assertThat(Iban.isValid("ES91 2100 0418 4502 0005 133")).isFalse();
		assertThat(Iban.isValid("XX91 2100 0418 4502 0005 1332")).isFalse();
	}

	@Example
	void knownEmails() {
		assertThat(Email.isValid("ana@acme.test")).isTrue();
		assertThat(Email.isValid("ana.lopez+facturas@sub.acme.es")).isTrue();
		assertThat(Email.of(" Ana@ACME.ES ").value()).isEqualTo("Ana@acme.es");
		assertThat(Email.isValid("ana@acme")).isFalse();
		assertThat(Email.isValid("ana acme@acme.es")).isFalse();
		assertThat(Email.isValid(".ana@acme.es")).isFalse();
		assertThat(Email.isValid("ana..lopez@acme.es")).isFalse();
		assertThat(Email.isValid("ana@-acme.es")).isFalse();
		assertThat(Email.isValid(null)).isFalse();
	}

	@Provide
	Arbitrary<String> dnis() {
		return Arbitraries.integers().between(0, 99_999_999).map(n -> String.format("%08d", n) + DNI_LETTERS.charAt(n % 23));
	}

	@Provide
	Arbitrary<String> nies() {
		return Combinators.combine(Arbitraries.integers().between(0, 2), Arbitraries.integers().between(0, 9_999_999))
			.as((prefix, n) -> {
				int number = Integer.parseInt(prefix + String.format("%07d", n));
				return "XYZ".charAt(prefix) + String.format("%07d", n) + DNI_LETTERS.charAt(number % 23);
			});
	}

	/** IBAN español: control del CCC calculado con los pesos oficiales y control del IBAN con módulo 97. */
	@Provide
	Arbitrary<String> spanishIbans() {
		Arbitrary<String> eightDigits = Arbitraries.strings().numeric().ofLength(8);
		Arbitrary<String> tenDigits = Arbitraries.strings().numeric().ofLength(10);
		return Combinators.combine(eightDigits, tenDigits).as((bankAndBranch, account) -> {
			String ccc = bankAndBranch + Iban.cccControlDigit("00" + bankAndBranch) + Iban.cccControlDigit(account)
					+ account;
			// ES = 14 28; los dígitos de control son 98 − (número mod 97) con «00» provisional.
			int remainder = new BigInteger(ccc + "142800").mod(BigInteger.valueOf(97)).intValue();
			return "ES" + String.format("%02d", 98 - remainder) + ccc;
		});
	}

	@Provide
	Arbitrary<String> emails() {
		Arbitrary<String> local = Arbitraries.strings().withCharRange('a', 'z').numeric().withChars('_', '+').ofMinLength(1).ofMaxLength(20);
		Arbitrary<String> label = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(15);
		Arbitrary<String> tld = Arbitraries.of("es", "com", "test", "org", "cat");
		return Combinators.combine(local, label, tld).as((l, d, t) -> l + "@" + d + "." + t);
	}

}
