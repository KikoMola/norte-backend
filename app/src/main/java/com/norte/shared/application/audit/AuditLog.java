package com.norte.shared.application.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Revisiones guardadas de los registros auditados (paso 1.4), del tenant actual. La implementación usa Envers; cada
 * módulo marca sus entidades con {@code @Audited}.
 */
public interface AuditLog {

	/**
	 * Revisiones de un registro, de la más reciente a la más antigua.
	 * @param before solo las anteriores a esta revisión (paginación); vacío para empezar por la última
	 * @param limit cuántas como máximo
	 */
	<T> List<Revision<T>> revisionsOf(Class<T> type, Object id, OptionalLong before, int limit);

	/** Estado de un registro tras una revisión, con quién la hizo y cuándo. */
	record Revision<T>(long number, Instant occurredAt, Optional<Author> author, Kind kind, T state) {
	}

	/** Usuario que hizo el cambio; vacío si fue un proceso del sistema. */
	record Author(String id, String name) {
	}

	enum Kind {

		CREATED, UPDATED, DELETED

	}

}
