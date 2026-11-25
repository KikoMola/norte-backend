package com.norte.shared.infrastructure.audit;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.norte.shared.application.audit.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;

import org.springframework.stereotype.Repository;

/**
 * {@link AuditLog} con las consultas de Envers. RLS deja ver solo las revisiones del tenant de la transacción.
 */
@Repository
class EnversAuditLog implements AuditLog {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public <T> List<Revision<T>> revisionsOf(Class<T> type, Object id, OptionalLong before, int limit) {
		AuditQuery query = AuditReaderFactory.get(entityManager)
			.createQuery()
			// Entidad, revisión y tipo de cambio por fila; incluye los borrados.
			.forRevisionsOfEntity(type, false, true)
			.add(AuditEntity.id().eq(id))
			.addOrder(AuditEntity.revisionNumber().desc())
			.setMaxResults(limit);
		before.ifPresent(revision -> query.add(AuditEntity.revisionNumber().lt(revision)));
		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();
		return rows.stream().map(row -> toRevision(type, row)).toList();
	}

	private static <T> Revision<T> toRevision(Class<T> type, Object[] row) {
		var revision = (AuditRevision) row[1];
		Optional<Author> author = Optional.ofNullable(revision.userId())
			.map(userId -> new Author(userId, revision.userName()));
		Kind kind = switch ((RevisionType) row[2]) {
			case ADD -> Kind.CREATED;
			case MOD -> Kind.UPDATED;
			case DEL -> Kind.DELETED;
		};
		return new Revision<>(revision.id(), revision.occurredAt(), author, kind, type.cast(row[0]));
	}

}
