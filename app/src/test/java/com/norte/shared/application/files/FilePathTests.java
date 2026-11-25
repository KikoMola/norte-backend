package com.norte.shared.application.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Rutas de fichero dentro del espacio del tenant (paso 1.7). */
class FilePathTests {

	@Test
	void joinsSegments() {
		assertThat(FilePath.of("invoices", "2026", "f-2026-0001.pdf").value()).isEqualTo("invoices/2026/f-2026-0001.pdf");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "/invoices/a.pdf", "invoices/", "invoices//a.pdf", "../other-tenant/a.pdf",
			"invoices/../../a.pdf", "invoices/.hidden", "Invoices/a.pdf", "invoices/a b.pdf", "invoices\\a.pdf" })
	void rejectsPathsThatCouldEscapeOrAreAmbiguous(String path) {
		assertThatThrownBy(() -> new FilePath(path)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsPathsThatAreTooLong() {
		assertThatThrownBy(() -> new FilePath("a".repeat(FilePath.MAX_LENGTH + 1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
