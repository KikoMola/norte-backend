package com.norte.parties.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.norte.parties.application.CompanySort;
import com.norte.shared.api.problem.InvalidParameterException;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

class CompanyCursorTests {

	private static final UUID ID = UUID.fromString("0b6f1c3e-9a43-4d0e-8f1e-7f6a2b1c9d10");

	@Test
	void roundTripsNameCursor() {
		var keys = new LinkedHashMap<String, Object>();
		keys.put("name", "Acme Ibérica S.L.");
		keys.put("id", ID);

		String cursor = CompanyCursor.encode(CompanySort.NAME_ASC, ScrollPosition.forward(keys));
		var decoded = (KeysetScrollPosition) CompanyCursor.decode(cursor, CompanySort.NAME_ASC);

		assertThat(cursor).doesNotContain("Acme").matches("[A-Za-z0-9_-]+");
		assertThat(decoded.getKeys()).containsEntry("name", "Acme Ibérica S.L.").containsEntry("id", ID);
	}

	@Test
	void roundTripsCreatedAtCursorWithInstantType() {
		var keys = new LinkedHashMap<String, Object>();
		keys.put("createdAt", Instant.parse("2026-11-24T09:30:00Z"));
		keys.put("id", ID);

		String cursor = CompanyCursor.encode(CompanySort.CREATED_DESC, ScrollPosition.forward(keys));
		var decoded = (KeysetScrollPosition) CompanyCursor.decode(cursor, CompanySort.CREATED_DESC);

		assertThat(decoded.getKeys()).containsEntry("createdAt", Instant.parse("2026-11-24T09:30:00Z"));
	}

	@Test
	void noCursorMeansFirstPage() {
		assertThat(CompanyCursor.decode(null, CompanySort.NAME_ASC)).isEqualTo(ScrollPosition.keyset());
	}

	@Test
	void rejectsCursorOfAnotherSort() {
		var keys = new LinkedHashMap<String, Object>();
		keys.put("name", "Acme");
		keys.put("id", ID);
		String cursor = CompanyCursor.encode(CompanySort.NAME_ASC, ScrollPosition.forward(keys));

		assertThatThrownBy(() -> CompanyCursor.decode(cursor, CompanySort.NAME_DESC))
			.isInstanceOf(InvalidParameterException.class);
	}

	@Test
	void rejectsTamperedCursor() {
		assertThatThrownBy(() -> CompanyCursor.decode("not-a-cursor", CompanySort.NAME_ASC))
			.isInstanceOf(InvalidParameterException.class);
	}

}
