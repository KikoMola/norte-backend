package com.norte.parties.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import com.norte.parties.domain.Company;
import com.norte.parties.domain.Company.Address;
import com.norte.parties.domain.Company.Profile;
import com.norte.parties.domain.CompanyRepository;
import com.norte.shared.application.TenantContext;
import com.norte.shared.application.audit.History;
import com.norte.shared.application.validation.Violations;
import com.norte.shared.domain.TaxId;
import com.norte.shared.domain.error.ConflictException;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.StaleVersionException;
import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Empresas del maestro de terceros (pasos 0.22 y 1.10). */
@Service
public class CompanyService {

	static final String DUPLICATE_TAX_ID = "company.tax_id.duplicate";

	static final String TAX_ID_UNIQUE_CONSTRAINT = "company_tenant_tax_id_uk";

	/** Ningún id real coincide: así la consulta de duplicados no excluye nada en un alta. */
	private static final UUID NO_COMPANY = new UUID(0, 0);

	private final CompanyRepository companies;

	private final Clock clock;

	private final History history;

	CompanyService(CompanyRepository companies, Clock clock, History history) {
		this.companies = companies;
		this.clock = clock;
		this.history = history;
	}

	/** Alta mínima: razón social y NIF (paso 0.22). */
	@Transactional
	public Company create(String name, String rawTaxId) {
		return create(new CompanyInput(name, rawTaxId, null, null, null, null,
				new Address(null, null, null, null, null, "ES"), null, true, false));
	}

	@Transactional
	public Company create(CompanyInput input) {
		Valid valid = validate(input);
		if (companies.existsByTaxId(valid.taxId().value())) {
			throw duplicateTaxId();
		}
		Company company = Company.create(TenantContext.requireTenantId(), valid.name(), valid.taxId(), clock.instant());
		company.describe(valid.profile());
		return saveFlushing(company);
	}

	/** @param expectedVersion la versión que el usuario editó ({@code If-Match}); si ya no es la actual, 412 */
	@Transactional
	public Company update(UUID id, CompanyInput input, long expectedVersion) {
		Valid valid = validate(input);
		Company company = get(id);
		StaleVersionException.check(expectedVersion, company.version());
		if (companies.existsByTaxIdAndIdNot(valid.taxId().value(), id)) {
			throw duplicateTaxId();
		}
		company.update(valid.name(), valid.taxId(), valid.profile(), clock.instant());
		return saveFlushing(company);
	}

	@Transactional(readOnly = true)
	public Company get(UUID id) {
		return companies.findById(id)
			.orElseThrow(() -> new NotFoundException("company.not_found", "No existe la empresa"));
	}

	@Transactional(readOnly = true)
	public Window<Company> list(String query, CompanySort sort, int limit, ScrollPosition position) {
		Specification<Company> spec = query == null || query.isBlank() ? Specification.unrestricted()
				: matching(query.strip());
		return companies.findBy(spec, q -> q.sortBy(sort.sort()).limit(limit).scroll(position));
	}

	/**
	 * Empresas que podrían ser la misma antes de un alta o una edición (paso 1.10): mismo NIF o nombre parecido.
	 * @param excludedId la empresa que se edita; {@code null} en un alta
	 */
	@Transactional(readOnly = true)
	public List<Duplicate> duplicates(String name, String rawTaxId, UUID excludedId) {
		String taxId = rawTaxId != null && TaxId.isValid(rawTaxId) ? TaxId.of(rawTaxId).value() : "";
		String text = name == null ? "" : name.strip();
		if (text.isEmpty() && taxId.isEmpty()) {
			return List.of();
		}
		return companies.findPossibleDuplicates(text, taxId, excludedId == null ? NO_COMPANY : excludedId)
			.stream()
			.map(company -> new Duplicate(company,
					company.taxId().value().equals(taxId) ? Duplicate.Reason.TAX_ID : Duplicate.Reason.SIMILAR_NAME))
			.toList();
	}

	/** Historial de la empresa (paso 1.4), de lo más reciente a lo más antiguo. 404 si no existe en este tenant. */
	@Transactional(readOnly = true)
	public History.Page history(UUID id, OptionalLong before, int limit) {
		get(id);
		return history.of(Company.class, id, before, limit, CompanyService::historyFields);
	}

	/** Campos de la empresa que se enseñan en su historial, con los nombres del contrato. */
	static Map<String, String> historyFields(Company company) {
		Profile profile = company.profile();
		var fields = new LinkedHashMap<String, String>();
		fields.put("name", company.name());
		fields.put("taxId", company.taxId().value());
		fields.put("tradeName", profile.tradeName());
		fields.put("email", profile.email());
		fields.put("phone", profile.phone());
		fields.put("website", profile.website());
		fields.put("address.line1", profile.address().line1());
		fields.put("address.line2", profile.address().line2());
		fields.put("address.postalCode", profile.address().postalCode());
		fields.put("address.city", profile.address().city());
		fields.put("address.province", profile.address().province());
		fields.put("address.country", profile.address().country());
		fields.put("creditLimit", profile.creditLimit() == null ? null : profile.creditLimit().toPlainString());
		fields.put("isCustomer", String.valueOf(profile.customer()));
		fields.put("isSupplier", String.valueOf(profile.supplier()));
		return fields;
	}

	/** Todas las reglas a la vez, para que el usuario vea todos sus errores en un solo intento. */
	static Valid validate(CompanyInput input) {
		var check = new Violations();
		String name = check.required("name", input.name(), Company.NAME_MAX_LENGTH);
		String taxId = check.taxId("taxId", input.taxId(), true);
		String tradeName = check.optional("tradeName", input.tradeName(), 200);
		String email = check.email("email", input.email());
		String phone = check.phone("phone", input.phone());
		String website = check.website("website", input.website());
		Address raw = input.address();
		String country = check.country("address.country", raw.country());
		var address = new Address(check.optional("address.line1", raw.line1(), 200),
				check.optional("address.line2", raw.line2(), 200),
				check.postalCode("address.postalCode", raw.postalCode(), country),
				check.optional("address.city", raw.city(), 100), check.optional("address.province", raw.province(), 100),
				country);
		BigDecimal creditLimit = check.amount("creditLimit", input.creditLimit());
		if (!input.customer() && !input.supplier()) {
			check.add("isCustomer", "company.kind.required", "Tiene que ser cliente, proveedor o las dos cosas.");
		}
		check.throwIfAny();
		return new Valid(name, TaxId.of(taxId), new Profile(tradeName, email, phone, website, address, creditLimit,
				input.customer(), input.supplier()));
	}

	private Company saveFlushing(Company company) {
		try {
			return companies.saveAndFlush(company);
		}
		catch (DataIntegrityViolationException ex) {
			// Dos altas o ediciones simultáneas con el mismo NIF: la restricción única decide.
			if (ex.getCause() instanceof ConstraintViolationException violation
					&& TAX_ID_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
				throw duplicateTaxId();
			}
			throw ex;
		}
	}

	/** Busca por nombre o NIF sin distinguir mayúsculas; los comodines de LIKE del texto se escapan. */
	private static Specification<Company> matching(String query) {
		String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		String name = "%" + escaped.toLowerCase(Locale.ROOT) + "%";
		String taxId = "%" + escaped.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "") + "%";
		return (root, criteria, cb) -> cb.or(cb.like(cb.lower(root.get("name")), name, '\\'),
				cb.like(root.get("taxId"), taxId, '\\'));
	}

	private static ConflictException duplicateTaxId() {
		return new ConflictException(DUPLICATE_TAX_ID, "Ya existe una empresa con ese NIF");
	}

	/**
	 * Datos de una empresa tal como llegan del usuario, sin validar.
	 * @param creditLimit en euros; {@code null} sin límite
	 */
	public record CompanyInput(String name, String taxId, String tradeName, String email, String phone,
			String website, Address address, BigDecimal creditLimit, boolean customer, boolean supplier) {
	}

	record Valid(String name, TaxId taxId, Profile profile) {
	}

	/** Una empresa que podría ser la misma, y por qué. */
	public record Duplicate(Company company, Reason reason) {

		public enum Reason {

			TAX_ID, SIMILAR_NAME

		}

	}

}
