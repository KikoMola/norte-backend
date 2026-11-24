package com.norte.parties.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.norte.shared.domain.TaxId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Empresa del maestro de terceros. De momento solo razón social y NIF (paso 0.22); el modelo completo llega en el
 * paso 1.10.
 */
@Entity
@Table(schema = "parties", name = "company")
public class Company {

	public static final int NAME_MAX_LENGTH = 200;

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(name = "tax_id", nullable = false, length = 15)
	private String taxId;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Company() {
	}

	private Company(UUID id, UUID tenantId, String name, TaxId taxId, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.name = name;
		this.taxId = taxId.value();
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * @param name razón social; se recortan los espacios de los extremos y no puede quedar vacía
	 */
	public static Company create(UUID tenantId, String name, TaxId taxId, Instant now) {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(taxId, "taxId");
		Objects.requireNonNull(now, "now");
		// PostgreSQL guarda microsegundos: se trunca aquí para que la respuesta del alta y las lecturas coincidan.
		return new Company(UUID.randomUUID(), tenantId, normalizeName(name), taxId, now.truncatedTo(ChronoUnit.MICROS));
	}

	static String normalizeName(String name) {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Company name must not be blank");
		}
		if (trimmed.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("Company name is longer than " + NAME_MAX_LENGTH);
		}
		return trimmed;
	}

	public UUID id() {
		return id;
	}

	public UUID tenantId() {
		return tenantId;
	}

	public String name() {
		return name;
	}

	public TaxId taxId() {
		return new TaxId(taxId);
	}

	/** Versión para el ETag; {@code 0} hasta que se persiste. */
	public long version() {
		return version == null ? 0 : version;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

}
