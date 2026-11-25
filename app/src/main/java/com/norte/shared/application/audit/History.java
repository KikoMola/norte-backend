package com.norte.shared.application.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import com.norte.shared.application.audit.AuditLog.Author;
import com.norte.shared.application.audit.AuditLog.Kind;
import com.norte.shared.application.audit.AuditLog.Revision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de un registro (paso 1.4): sus revisiones convertidas en eventos con los campos que cambiaron.
 *
 * <p>Cada módulo decide qué campos enseña y cómo se escriben con una función {@code estado → campos}. Así el
 * historial habla el idioma del contrato ({@code taxId}, no {@code tax_id}) y los campos técnicos (versión, fecha de
 * modificación) no aparecen. Una revisión que solo toca campos que no se enseñan no sale en el historial.
 */
@Service
public class History {

	private final AuditLog log;

	History(AuditLog log) {
		this.log = log;
	}

	/**
	 * @param before revisión a partir de la que seguir (el cursor); vacío para empezar por la más reciente
	 * @param fields campos que se enseñan, con su valor como texto; el orden del mapa es el de los cambios
	 */
	@Transactional(readOnly = true)
	public <T> Page of(Class<T> type, Object id, OptionalLong before, int limit, Function<T, Map<String, String>> fields) {
		// Una de más: dice si hay otra página y es el estado anterior del último evento de esta.
		List<Revision<T>> revisions = log.revisionsOf(type, id, before, limit + 1);
		boolean hasNext = revisions.size() > limit;
		var events = new ArrayList<Event>();
		for (int i = 0; i < Math.min(limit, revisions.size()); i++) {
			Revision<T> revision = revisions.get(i);
			Map<String, String> after = fields.apply(revision.state());
			Optional<Map<String, String>> previous = i + 1 < revisions.size()
					? Optional.of(fields.apply(revisions.get(i + 1).state())) : Optional.empty();
			List<Change> changes = switch (revision.kind()) {
				case CREATED, DELETED -> List.of();
				case UPDATED -> previous.map(p -> diff(p, after)).orElse(List.of());
			};
			if (revision.kind() == Kind.UPDATED && changes.isEmpty()) {
				continue;
			}
			events.add(new Event(revision.number(), revision.occurredAt(), revision.author(), revision.kind(), changes));
		}
		OptionalLong next = hasNext ? OptionalLong.of(revisions.get(limit - 1).number()) : OptionalLong.empty();
		return new Page(events, next);
	}

	static List<Change> diff(Map<String, String> before, Map<String, String> after) {
		var names = new LinkedHashSet<String>(after.keySet());
		names.addAll(before.keySet());
		var changes = new ArrayList<Change>();
		for (String name : names) {
			String from = before.get(name);
			String to = after.get(name);
			if (!Objects.equals(from, to)) {
				changes.add(new Change(name, from, to));
			}
		}
		return changes;
	}

	/** Página del historial; {@code next} es la revisión desde la que pedir la siguiente. */
	public record Page(List<Event> events, OptionalLong next) {
	}

	public record Event(long revision, Instant occurredAt, Optional<Author> author, Kind kind, List<Change> changes) {
	}

	/** Un campo que cambió; {@code null} si no tenía valor. */
	public record Change(String field, String from, String to) {
	}

}
