package com.norte.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaxIdTests {

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678Z", // DNI
			"00000000T", // DNI con ceros
			"X1234567L", // NIE con X
			"Y1234567X", // NIE con Y
			"Z1234567R", // NIE con Z
			"K1234567L", // NIF especial K
			"B12345674", // CIF con dígito de control (sociedad limitada)
			"A58818501", // CIF con dígito de control (sociedad anónima)
			"Q2826000H", // CIF con letra de control (organismo público)
			"G12345674", // CIF que admite dígito…
			"G1234567D" // …o letra
	})
	void acceptsValidSpanishTaxIds(String value) {
		assertThat(TaxId.isValid(value)).isTrue();
		assertThat(TaxId.of(value).value()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"12345678A", // letra de DNI incorrecta
			"X1234567A", // letra de NIE incorrecta
			"B12345675", // dígito de CIF incorrecto
			"B1234567D", // una S.L. no lleva letra de control
			"Q28260008", // un organismo público no lleva dígito de control
			"I12345674", // letra inicial que no existe
			"1234567Z", // longitud
			"", "ABCDEFGHI" })
	void rejectsInvalidTaxIds(String value) {
		assertThat(TaxId.isValid(value)).isFalse();
		assertThatThrownBy(() -> TaxId.of(value)).isInstanceOf(TaxId.InvalidTaxIdException.class);
	}

	@Test
	void normalizesCaseSpacesDotsAndDashes() {
		assertThat(TaxId.of(" b-12.345.674 ").value()).isEqualTo("B12345674");
		assertThat(TaxId.of("12345678z").value()).isEqualTo("12345678Z");
	}

	@Test
	void rejectsNull() {
		assertThat(TaxId.isValid(null)).isFalse();
	}

}
