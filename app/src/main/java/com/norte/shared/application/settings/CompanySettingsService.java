package com.norte.shared.application.settings;

import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.norte.shared.application.TenantContext;
import com.norte.shared.application.audit.History;
import com.norte.shared.application.files.FilePath;
import com.norte.shared.application.files.FileStorage;
import com.norte.shared.application.validation.Violations;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.StaleVersionException;
import com.norte.shared.domain.error.ValidationException;
import com.norte.shared.domain.settings.CompanySettings;
import com.norte.shared.domain.settings.CompanySettings.Details;
import com.norte.shared.domain.settings.CompanySettingsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajustes de la empresa del tenant (paso 1.9): sus datos como emisor, el logo y la región (ejercicio, zona horaria,
 * idioma de los documentos).
 */
@Service
public class CompanySettingsService {

	/** Idiomas de los documentos y correos a clientes; los mismos que la interfaz. */
	public static final Set<String> LANGUAGES = Set.of("es", "en");

	public static final int LOGO_MAX_BYTES = 512 * 1024;

	private final CompanySettingsRepository settings;

	private final FileStorage files;

	private final History history;

	private final Clock clock;

	CompanySettingsService(CompanySettingsRepository settings, FileStorage files, History history, Clock clock) {
		this.settings = settings;
		this.files = files;
		this.history = history;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CompanySettings current() {
		return settings.findById(TenantContext.requireTenantId())
			// Solo pasa con un tenant dado de alta antes del paso 1.9: repetir el alta los crea.
			.orElseThrow(() -> new NotFoundException("settings.not_found", "La empresa aún no tiene ajustes"));
	}

	/**
	 * @param expectedVersion la versión que el usuario editó ({@code If-Match}); si ya no es la actual, 412
	 */
	@Transactional
	public CompanySettings update(Details details, long expectedVersion) {
		Details normalized = validate(details);
		CompanySettings current = current();
		StaleVersionException.check(expectedVersion, current.version());
		current.update(normalized, clock.instant());
		return settings.saveAndFlush(current);
	}

	/**
	 * Sustituye el logo. El tipo se deduce de los primeros bytes, no del que declara el navegador: solo PNG, JPEG y
	 * WebP. SVG no se admite porque puede llevar scripts.
	 */
	@Transactional
	public CompanySettings changeLogo(byte[] content, long expectedVersion) {
		LogoFormat format = LogoFormat.detect(content)
			.orElseThrow(() -> ValidationException.of("file", "logo.unsupported_type",
					"El logo tiene que ser una imagen PNG, JPEG o WebP."));
		if (content.length > LOGO_MAX_BYTES) {
			throw ValidationException.of("file", "logo.too_large", "El logo no puede pasar de 512 KB.");
		}
		CompanySettings current = current();
		StaleVersionException.check(expectedVersion, current.version());
		Optional<String> previous = current.logoPath();
		// Cada logo tiene su ruta: una URL firmada antigua nunca enseña el nuevo, ni al revés.
		var path = FilePath.of("settings", "logo-" + UUID.randomUUID() + "." + format.extension());
		files.put(path, content, format.contentType());
		current.changeLogo(path.value(), format.contentType(), clock.instant());
		CompanySettings saved = settings.saveAndFlush(current);
		previous.map(FilePath::new).ifPresent(files::delete);
		return saved;
	}

	@Transactional
	public CompanySettings removeLogo(long expectedVersion) {
		CompanySettings current = current();
		StaleVersionException.check(expectedVersion, current.version());
		Optional<String> previous = current.logoPath();
		current.removeLogo(clock.instant());
		CompanySettings saved = settings.saveAndFlush(current);
		previous.map(FilePath::new).ifPresent(files::delete);
		return saved;
	}

	/** URL firmada para enseñar el logo en la pantalla de ajustes. */
	public Optional<URI> logoUrl(CompanySettings company) {
		return company.logoPath()
			.map(path -> files.signedUrl(new FilePath(path), FileStorage.DEFAULT_URL_VALIDITY,
					"logo." + path.substring(path.lastIndexOf('.') + 1)));
	}

	@Transactional(readOnly = true)
	public History.Page history(OptionalLong before, int limit) {
		return history.of(CompanySettings.class, current().tenantId(), before, limit,
				CompanySettingsService::historyFields);
	}

	/** Campos del historial con los nombres del contrato. El logo no sale: su valor es una ruta interna. */
	static Map<String, String> historyFields(CompanySettings company) {
		Details details = company.details();
		var fields = new LinkedHashMap<String, String>();
		fields.put("legalName", details.legalName());
		fields.put("tradeName", details.tradeName());
		fields.put("taxId", details.taxId());
		fields.put("address.line1", details.addressLine1());
		fields.put("address.line2", details.addressLine2());
		fields.put("address.postalCode", details.postalCode());
		fields.put("address.city", details.city());
		fields.put("address.province", details.province());
		fields.put("address.country", details.country());
		fields.put("email", details.email());
		fields.put("phone", details.phone());
		fields.put("website", details.website());
		fields.put("fiscalYearStartMonth", String.valueOf(details.fiscalYearStartMonth()));
		fields.put("timeZone", details.timeZone());
		fields.put("language", details.language());
		return fields;
	}

	/** Todas las reglas a la vez, para que el usuario vea todos sus errores en un solo intento. */
	static Details validate(Details details) {
		var check = new Violations();
		String legalName = check.required("legalName", details.legalName(), 200);
		String tradeName = check.optional("tradeName", details.tradeName(), 200);
		String taxId = check.taxId("taxId", details.taxId(), false);
		String country = check.country("address.country", details.country());
		String line1 = check.optional("address.line1", details.addressLine1(), 200);
		String line2 = check.optional("address.line2", details.addressLine2(), 200);
		String postalCode = check.postalCode("address.postalCode", details.postalCode(), country);
		String city = check.optional("address.city", details.city(), 100);
		String province = check.optional("address.province", details.province(), 100);
		String email = check.email("email", details.email());
		String phone = check.phone("phone", details.phone());
		String website = check.website("website", details.website());
		if (details.fiscalYearStartMonth() < 1 || details.fiscalYearStartMonth() > 12) {
			check.add("fiscalYearStartMonth", "month.invalid", "El mes tiene que ir de 1 a 12.");
		}
		String timeZone = details.timeZone() == null ? null : details.timeZone().strip();
		if (!isZone(timeZone)) {
			check.add("timeZone", "time_zone.invalid", "La zona horaria no es válida.");
		}
		String language = details.language() == null ? null : details.language().strip();
		if (language == null || !LANGUAGES.contains(language)) {
			check.add("language", "language.unsupported", "El idioma no está disponible.");
		}
		check.throwIfAny();
		return new Details(legalName, tradeName, taxId, line1, line2, postalCode, city, province, country, email, phone,
				website, details.fiscalYearStartMonth(), timeZone, language);
	}

	private static boolean isZone(String zone) {
		if (zone == null) {
			return false;
		}
		try {
			// Solo identificadores IANA de región; «+02:00» o «GMT» no cambian con el horario de verano.
			return zone.contains("/") && ZoneId.of(zone).getId().equals(zone);
		}
		catch (DateTimeException ex) {
			return false;
		}
	}

	/** Formatos de logo admitidos, reconocidos por su firma de bytes. */
	enum LogoFormat {

		PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp");

		private final String contentType;

		private final String extension;

		LogoFormat(String contentType, String extension) {
			this.contentType = contentType;
			this.extension = extension;
		}

		String contentType() {
			return contentType;
		}

		String extension() {
			return extension;
		}

		static Optional<LogoFormat> detect(byte[] content) {
			if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
				return Optional.of(PNG);
			}
			if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
				return Optional.of(JPEG);
			}
			if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
				return Optional.of(WEBP);
			}
			return Optional.empty();
		}

		private static boolean startsWith(byte[] content, int offset, int... signature) {
			if (content.length < offset + signature.length) {
				return false;
			}
			for (int i = 0; i < signature.length; i++) {
				if ((content[offset + i] & 0xFF) != signature[i]) {
					return false;
				}
			}
			return true;
		}

	}

}
