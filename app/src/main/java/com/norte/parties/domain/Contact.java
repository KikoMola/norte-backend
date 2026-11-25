package com.norte.parties.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

/**
 * Persona de contacto de una empresa (paso 1.10), con su función: a quién mandar las facturas, quién decide, quién
 * compra. Sus cambios quedan en el historial.
 */
@Entity
@Audited
@Table(schema = "parties", name = "contact")
public class Contact {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "company_id", nullable = false, updatable = false)
	private UUID companyId;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "job_title")
	private String jobTitle;

	private String email;

	private String phone;

	private String role;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Contact() {
	}

	public static Contact create(UUID tenantId, UUID companyId, Details details, Instant now) {
		var contact = new Contact();
		contact.id = UUID.randomUUID();
		contact.tenantId = Objects.requireNonNull(tenantId, "tenantId");
		contact.companyId = Objects.requireNonNull(companyId, "companyId");
		contact.createdAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
		contact.apply(details, now);
		return contact;
	}

	public void update(Details details, Instant now) {
		apply(details, now);
	}

	private void apply(Details details, Instant now) {
		if (details.firstName() == null || details.firstName().isBlank()) {
			throw new IllegalArgumentException("Contact first name must not be blank");
		}
		this.firstName = details.firstName();
		this.lastName = details.lastName();
		this.jobTitle = details.jobTitle();
		this.email = details.email();
		this.phone = details.phone();
		this.role = details.role().map(Role::code).orElse(null);
		this.updatedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
	}

	public UUID id() {
		return id;
	}

	public UUID companyId() {
		return companyId;
	}

	public Details details() {
		return new Details(firstName, lastName, jobTitle, email, phone, Role.fromCode(role));
	}

	/** Nombre para enseñar: nombre y apellidos. */
	public String fullName() {
		return lastName == null ? firstName : firstName + " " + lastName;
	}

	public long version() {
		return version == null ? 0 : version;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	/** Datos editables, ya normalizados (vacío como {@code null}). */
	public record Details(String firstName, String lastName, String jobTitle, String email, String phone,
			Optional<Role> role) {

		public Details {
			role = Objects.requireNonNull(role, "role");
		}

	}

	/** Función del contacto. El código es el del contrato y la base de datos. */
	public enum Role {

		BILLING("billing"), DECISION_MAKER("decision_maker"), PURCHASING("purchasing"), TECHNICAL("technical"),
		OTHER("other");

		private final String code;

		Role(String code) {
			this.code = code;
		}

		public String code() {
			return code;
		}

		public static Optional<Role> fromCode(String code) {
			return Arrays.stream(values()).filter(role -> role.code.equals(code)).findFirst();
		}

	}

}
