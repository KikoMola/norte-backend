package com.norte.shared.application.settings;

import java.net.URI;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.norte.shared.application.TenantContext;
import com.norte.shared.application.audit.History;
import com.norte.shared.application.files.FilePath;
import com.norte.shared.application.files.FileStorage;
import com.norte.shared.domain.Email;
import com.norte.shared.domain.TaxId;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.StaleVersionException;
import com.norte.shared.domain.error.ValidationException;
import com.norte.shared.domain.error.ValidationException.Violation;
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

	private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

	private static final Pattern SPANISH_POSTAL_CODE = Pattern.compile("(0[1-9]|[1-4]\\d|5[0-2])\\d{3}");

	private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ()-]{6,30}");

	private static final Pattern WEBSITE = Pattern.compile("(https?://)?[\\p{L}0-9.-]+\\.[\\p{L}]{2,}(/\\S*)?",
			Pattern.CASE_INSENSITIVE);

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
		var violations = new ArrayList<Violation>();
		String legalName = strip(details.legalName());
		if (legalName == null) {
			violations.add(new Violation("legalName", "required", "La razón social es obligatoria."));
		}
		maxLength(violations, "legalName", legalName, 200);
		maxLength(violations, "tradeName", details.tradeName(), 200);

		String taxId = strip(details.taxId());
		if (taxId != null) {
			if (TaxId.isValid(taxId)) {
				taxId = TaxId.of(taxId).value();
			}
			else {
				violations.add(new Violation("taxId", "tax_id.invalid", "El NIF no es válido."));
			}
		}

		String country = strip(details.country());
		if (country == null || !COUNTRY.matcher(country).matches()) {
			violations.add(new Violation("address.country", "country.invalid", "El país no es válido."));
		}
		maxLength(violations, "address.line1", details.addressLine1(), 200);
		maxLength(violations, "address.line2", details.addressLine2(), 200);
		maxLength(violations, "address.city", details.city(), 100);
		maxLength(violations, "address.province", details.province(), 100);
		String postalCode = strip(details.postalCode());
		if (postalCode != null && "ES".equals(country) && !SPANISH_POSTAL_CODE.matcher(postalCode).matches()) {
			violations.add(new Violation("address.postalCode", "postal_code.invalid",
					"El código postal español tiene 5 cifras y empieza por 01 a 52."));
		}
		maxLength(violations, "address.postalCode", postalCode, 20);

		String email = strip(details.email());
		if (email != null) {
			if (Email.isValid(email)) {
				email = Email.of(email).value();
			}
			else {
				violations.add(new Violation("email", "email.invalid", "El correo no es válido."));
			}
		}
		String phone = strip(details.phone());
		if (phone != null && !PHONE.matcher(phone).matches()) {
			violations.add(new Violation("phone", "phone.invalid", "El teléfono no es válido."));
		}
		String website = strip(details.website());
		if (website != null && (website.length() > 200 || !WEBSITE.matcher(website).matches())) {
			violations.add(new Violation("website", "website.invalid", "La web no es válida."));
		}

		if (details.fiscalYearStartMonth() < 1 || details.fiscalYearStartMonth() > 12) {
			violations.add(new Violation("fiscalYearStartMonth", "month.invalid", "El mes tiene que ir de 1 a 12."));
		}
		String timeZone = strip(details.timeZone());
		if (!isZone(timeZone)) {
			violations.add(new Violation("timeZone", "time_zone.invalid", "La zona horaria no es válida."));
		}
		String language = strip(details.language());
		if (language == null || !LANGUAGES.contains(language)) {
			violations.add(new Violation("language", "language.unsupported", "El idioma no está disponible."));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException(violations);
		}
		return new Details(legalName, details.tradeName(), taxId, details.addressLine1(), details.addressLine2(),
				postalCode, details.city(), details.province(), country, email, phone, website,
				details.fiscalYearStartMonth(), timeZone, language);
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

	private static void maxLength(List<Violation> violations, String field, String value, int max) {
		if (value != null && value.strip().length() > max) {
			violations.add(new Violation(field, "too_long", "Como mucho " + max + " caracteres."));
		}
	}

	private static String strip(String value) {
		return value == null || value.isBlank() ? null : value.strip();
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
