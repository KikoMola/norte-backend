package com.norte.shared.domain.settings;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Datos de la empresa del tenant como emisor de facturas (paso 1.9). Uno por tenant: su identificador es el del
 * tenant. Sus cambios quedan en el historial (paso 1.4).
 *
 * <p>Valida solo lo que es estructural (no vacíos, rangos); las reglas con mensaje para el usuario (NIF, correo,
 * zona horaria) las comprueba el servicio antes, para devolverlas todas juntas en un 422.
 */
@Entity
@Audited
@Table(schema = "shared", name = "company_settings")
public class CompanySettings {

	@Id
	@Column(name = "tenant_id")
	private UUID tenantId;

	@Column(name = "legal_name", nullable = false)
	private String legalName;

	@Column(name = "trade_name")
	private String tradeName;

	@Column(name = "tax_id")
	private String taxId;

	@Column(name = "address_line1")
	private String addressLine1;

	@Column(name = "address_line2")
	private String addressLine2;

	@Column(name = "postal_code")
	private String postalCode;

	private String city;

	private String province;

	@Column(nullable = false)
	private String country;

	private String email;

	private String phone;

	private String website;

	@Column(name = "fiscal_year_start_month", nullable = false)
	private int fiscalYearStartMonth;

	@Column(name = "time_zone", nullable = false)
	private String timeZone;

	@Column(nullable = false)
	private String language;

	@Column(name = "logo_path")
	private String logoPath;

	@Column(name = "logo_content_type")
	private String logoContentType;

	@Version
	private Long version;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CompanySettings() {
	}

	/** Ajustes iniciales del alta de tenant: solo la razón social; el resto con los valores por defecto. */
	public static CompanySettings initial(UUID tenantId, String legalName, Instant now) {
		var settings = new CompanySettings();
		settings.tenantId = Objects.requireNonNull(tenantId, "tenantId");
		settings.apply(new Details(legalName, null, null, null, null, null, null, null, "ES", null, null, null, 1,
				"Europe/Madrid", "es"), now);
		return settings;
	}

	public void update(Details details, Instant now) {
		apply(details, now);
	}

	public void changeLogo(String path, String contentType, Instant now) {
		this.logoPath = Objects.requireNonNull(path, "path");
		this.logoContentType = Objects.requireNonNull(contentType, "contentType");
		touch(now);
	}

	public void removeLogo(Instant now) {
		this.logoPath = null;
		this.logoContentType = null;
		touch(now);
	}

	private void apply(Details details, Instant now) {
		if (details.legalName() == null || details.legalName().isBlank()) {
			throw new IllegalArgumentException("Legal name must not be blank");
		}
		if (details.fiscalYearStartMonth() < 1 || details.fiscalYearStartMonth() > 12) {
			throw new IllegalArgumentException("Fiscal year start month must be between 1 and 12");
		}
		this.legalName = details.legalName().strip();
		this.tradeName = blankToNull(details.tradeName());
		this.taxId = blankToNull(details.taxId());
		this.addressLine1 = blankToNull(details.addressLine1());
		this.addressLine2 = blankToNull(details.addressLine2());
		this.postalCode = blankToNull(details.postalCode());
		this.city = blankToNull(details.city());
		this.province = blankToNull(details.province());
		this.country = Objects.requireNonNull(details.country(), "country");
		this.email = blankToNull(details.email());
		this.phone = blankToNull(details.phone());
		this.website = blankToNull(details.website());
		this.fiscalYearStartMonth = details.fiscalYearStartMonth();
		this.timeZone = Objects.requireNonNull(details.timeZone(), "timeZone");
		this.language = Objects.requireNonNull(details.language(), "language");
		touch(now);
	}

	private void touch(Instant now) {
		this.updatedAt = Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.MICROS);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public UUID tenantId() {
		return tenantId;
	}

	public Details details() {
		return new Details(legalName, tradeName, taxId, addressLine1, addressLine2, postalCode, city, province, country,
				email, phone, website, fiscalYearStartMonth, timeZone, language);
	}

	public Optional<String> logoPath() {
		return Optional.ofNullable(logoPath);
	}

	public Optional<String> logoContentType() {
		return Optional.ofNullable(logoContentType);
	}

	/** Versión para el ETag; {@code 0} hasta que se persiste. */
	public long version() {
		return version == null ? 0 : version;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	/**
	 * Lo que el usuario edita. Los opcionales van a {@code null} si están vacíos.
	 * @param taxId NIF ya normalizado (sin espacios ni guiones, en mayúsculas)
	 * @param country ISO 3166-1 alfa-2
	 * @param timeZone identificador IANA ({@code Europe/Madrid})
	 * @param language idioma de documentos y correos a clientes ({@code es}, {@code en})
	 */
	public record Details(String legalName, String tradeName, String taxId, String addressLine1, String addressLine2,
			String postalCode, String city, String province, String country, String email, String phone,
			String website, int fiscalYearStartMonth, String timeZone, String language) {
	}

}
