package com.norte.shared.infrastructure.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Revisión de Envers (paso 1.4): una por transacción que cambia algo auditado, con el tenant y quién la hizo. La
 * rellena {@link AuditRevisionListener}.
 */
@Entity
@Table(schema = "shared", name = "revision")
@RevisionEntity(AuditRevisionListener.class)
class AuditRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revision_seq")
	@SequenceGenerator(name = "revision_seq", schema = "shared", sequenceName = "revision_seq", allocationSize = 1)
	@RevisionNumber
	private long id;

	@RevisionTimestamp
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	/** {@code sub} del token; {@code null} si el cambio no lo hizo un usuario (un proceso del sistema). */
	@Column(name = "user_id")
	private String userId;

	@Column(name = "user_name")
	private String userName;

	long id() {
		return id;
	}

	Instant occurredAt() {
		return occurredAt;
	}

	String userId() {
		return userId;
	}

	String userName() {
		return userName;
	}

	void assign(UUID tenantId, String userId, String userName) {
		this.tenantId = tenantId;
		this.userId = userId;
		this.userName = userName;
	}

}
