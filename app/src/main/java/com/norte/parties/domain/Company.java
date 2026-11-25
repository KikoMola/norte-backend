package com.norte.parties.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.domain.TaxId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

/**
 * Empresa del maestro de terceros (pasos 0.22 y 1.10): razón social, NIF único por tenant y su perfil (contacto,
 * dirección fiscal, límite de crédito, si es cliente o proveedor). Sus cambios quedan en el historial (paso 1.4).
 *
 * <p>La validación con mensajes para el usuario la hace el servicio; aquí solo lo estructural.
 */
@Entity
@Audited
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

	@Column(name = "trade_name")
	private String tradeName;

	private String email;

	private String phone;

	private String website;

	@Column(name = "address_line1")
	private String addressLine1;

	@Column(name = "address_line2")
	private String addressLine2;

	@Column(name = "postal_code")
	private String postalCode;

	private String city;

	private String province;

	@Column(nullable = false)
	private String country = "ES";

	@Column(name = "credit_limit")
	private BigDecimal creditLimit;

	@Column(name = "is_customer", nullable = false)
	private boolean customer = true;

	@Column(name = "is_supplier", nullable = false)
	private boolean supplier;

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

	/** Sustituye los datos editables. El NIF ya llega normalizado y comprobado contra duplicados. */
	public void update(String name, TaxId taxId, Profile profile, Instant now) {
		Objects.requireNonNull(taxId, "taxId");
		Objects.requireNonNull(now, "now");
		this.name = normalizeName(name);
		this.taxId = taxId.value();
		apply(profile);
		this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
	}

	/** Perfil del alta; sin él, el de por defecto (cliente, España). */
	public void describe(Profile profile) {
		apply(profile);
	}

	private void apply(Profile profile) {
		Objects.requireNonNull(profile, "profile");
		if (profile.creditLimit() != null && profile.creditLimit().signum() < 0) {
			throw new IllegalArgumentException("Credit limit must not be negative");
		}
		this.tradeName = profile.tradeName();
		this.email = profile.email();
		this.phone = profile.phone();
		this.website = profile.website();
		this.addressLine1 = profile.address().line1();
		this.addressLine2 = profile.address().line2();
		this.postalCode = profile.address().postalCode();
		this.city = profile.address().city();
		this.province = profile.address().province();
		this.country = Objects.requireNonNull(profile.address().country(), "country");
		this.creditLimit = profile.creditLimit();
		this.customer = profile.customer();
		this.supplier = profile.supplier();
	}

	/** Cambia la razón social; con las mismas reglas que al crearla. */
	public void rename(String name, Instant now) {
		Objects.requireNonNull(now, "now");
		this.name = normalizeName(name);
		this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
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

	public Profile profile() {
		return new Profile(tradeName, email, phone, website,
				new Address(addressLine1, addressLine2, postalCode, city, province, country), creditLimit, customer,
				supplier);
	}

	/** Límite de crédito en euros; vacío si no tiene. */
	public Optional<BigDecimal> creditLimit() {
		return Optional.ofNullable(creditLimit);
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

	/**
	 * Lo editable además de la razón social y el NIF. Los textos ya llegan normalizados (vacío como {@code null}).
	 * @param creditLimit en euros; {@code null} sin límite
	 */
	public record Profile(String tradeName, String email, String phone, String website, Address address,
			BigDecimal creditLimit, boolean customer, boolean supplier) {

		/** Cliente en España, sin más datos: el alta mínima. */
		public static Profile empty() {
			return new Profile(null, null, null, null, Address.spain(), null, true, false);
		}

	}

	/** Dirección fiscal; solo el país es obligatorio. */
	public record Address(String line1, String line2, String postalCode, String city, String province,
			String country) {

		public static Address spain() {
			return new Address(null, null, null, null, null, "ES");
		}

	}

}
