package com.norte.parties.application;

import java.time.Clock;
import java.util.UUID;

import com.norte.parties.domain.Company;
import com.norte.parties.domain.CompanyRepository;
import com.norte.shared.application.TenantContext;
import com.norte.shared.domain.TaxId;
import com.norte.shared.domain.error.ConflictException;
import com.norte.shared.domain.error.NotFoundException;
import com.norte.shared.domain.error.ValidationException;
import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

	static final String DUPLICATE_TAX_ID = "company.tax_id.duplicate";

	static final String TAX_ID_UNIQUE_CONSTRAINT = "company_tenant_tax_id_uk";

	private final CompanyRepository companies;

	private final Clock clock;

	CompanyService(CompanyRepository companies, Clock clock) {
		this.companies = companies;
		this.clock = clock;
	}

	@Transactional
	public Company create(String name, String rawTaxId) {
		if (name == null || name.isBlank()) {
			throw ValidationException.of("name", "required", "La razón social es obligatoria.");
		}
		if (!TaxId.isValid(rawTaxId)) {
			throw ValidationException.of("taxId", "tax_id.invalid", "El NIF no es válido.");
		}
		TaxId taxId = TaxId.of(rawTaxId);
		if (companies.existsByTaxId(taxId.value())) {
			throw duplicateTaxId();
		}
		Company company = Company.create(TenantContext.requireTenantId(), name, taxId, clock.instant());
		try {
			return companies.saveAndFlush(company);
		}
		catch (DataIntegrityViolationException ex) {
			// Dos altas simultáneas con el mismo NIF: la restricción única decide.
			if (ex.getCause() instanceof ConstraintViolationException violation
					&& TAX_ID_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
				throw duplicateTaxId();
			}
			throw ex;
		}
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

	/** Busca por nombre o NIF sin distinguir mayúsculas; los comodines de LIKE del texto se escapan. */
	private static Specification<Company> matching(String query) {
		String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		String name = "%" + escaped.toLowerCase(java.util.Locale.ROOT) + "%";
		String taxId = "%" + escaped.toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s.\\-]", "") + "%";
		return (root, criteria, cb) -> cb.or(cb.like(cb.lower(root.get("name")), name, '\\'),
				cb.like(root.get("taxId"), taxId, '\\'));
	}

	private static ConflictException duplicateTaxId() {
		return new ConflictException(DUPLICATE_TAX_ID, "Ya existe una empresa con ese NIF");
	}

}
