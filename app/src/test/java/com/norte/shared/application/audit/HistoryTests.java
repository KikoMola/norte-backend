package com.norte.shared.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import com.norte.shared.application.audit.AuditLog.Kind;
import com.norte.shared.application.audit.AuditLog.Revision;
import com.norte.shared.application.audit.History.Change;
import org.junit.jupiter.api.Test;

/** Cálculo de cambios y paginación del historial, sin base de datos (paso 1.4). */
class HistoryTests {

	@Test
	void diffListsOnlyTheFieldsThatChangedInTheirOrder() {
		assertThat(History.diff(fields("Acme", "B12345674", null), fields("Acme Ibérica", "B12345674", "ES")))
			.containsExactly(new Change("name", "Acme", "Acme Ibérica"), new Change("country", null, "ES"));
	}

	@Test
	void revisionsThatOnlyTouchHiddenFieldsAreSkipped() {
		// La revisión 2 solo cambió algo que la función de campos no enseña.
		var log = new FakeLog(List.of(revision(3, Kind.UPDATED, "C"), revision(2, Kind.UPDATED, "B"),
				revision(1, Kind.CREATED, "B")));

		History.Page page = new History(log).of(String.class, "x", OptionalLong.empty(), 10, HistoryTests::nameOnly);

		assertThat(page.events()).extracting(History.Event::revision).containsExactly(3L, 1L);
		assertThat(page.events().getFirst().changes()).containsExactly(new Change("name", "B", "C"));
		assertThat(page.next()).isEmpty();
	}

	@Test
	void theNextCursorIsTheLastRevisionOfThePage() {
		var log = new FakeLog(List.of(revision(3, Kind.UPDATED, "C"), revision(2, Kind.UPDATED, "B"),
				revision(1, Kind.CREATED, "A")));

		History.Page page = new History(log).of(String.class, "x", OptionalLong.empty(), 2, HistoryTests::nameOnly);

		assertThat(page.events()).extracting(History.Event::revision).containsExactly(3L, 2L);
		assertThat(page.events().get(1).changes()).containsExactly(new Change("name", "A", "B"));
		assertThat(page.next()).hasValue(2L);
	}

	private static Map<String, String> fields(String name, String taxId, String country) {
		var fields = new LinkedHashMap<String, String>();
		fields.put("name", name);
		fields.put("taxId", taxId);
		fields.put("country", country);
		return fields;
	}

	private static Map<String, String> nameOnly(String state) {
		return Map.of("name", state);
	}

	private static Revision<String> revision(long number, Kind kind, String state) {
		return new Revision<>(number, Instant.EPOCH.plusSeconds(number), Optional.empty(), kind, state);
	}

	/** Revisiones de más reciente a más antigua, como las devuelve Envers. */
	private record FakeLog(List<Revision<String>> revisions) implements AuditLog {

		@Override
		@SuppressWarnings("unchecked")
		public <T> List<Revision<T>> revisionsOf(Class<T> type, Object id, OptionalLong before, int limit) {
			var result = new ArrayList<Revision<T>>();
			for (Revision<String> revision : revisions) {
				if (before.isEmpty() || revision.number() < before.getAsLong()) {
					result.add((Revision<T>) revision);
				}
			}
			return result.subList(0, Math.min(limit, result.size()));
		}

	}

}
