package com.norte.parties.api;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import com.norte.contract.api.CompaniesApi;
import com.norte.contract.model.AuditEventPageDto;
import com.norte.contract.model.CompanyCreateDto;
import com.norte.contract.model.CompanyDto;
import com.norte.contract.model.CompanyPageDto;
import com.norte.contract.model.PageInfoDto;
import com.norte.parties.application.CompanyService;
import com.norte.parties.application.CompanySort;
import com.norte.parties.domain.Company;
import com.norte.shared.api.AuditEvents;
import com.norte.shared.api.ETags;
import com.norte.shared.api.problem.InvalidParameterException;
import com.norte.shared.api.security.RequiresPermission;
import com.norte.shared.domain.access.Permission;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CompaniesController implements CompaniesApi {

	private static final String COMPANIES_PATH = "/api/v1/companies/";

	private final CompanyService companies;

	CompaniesController(CompanyService companies) {
		this.companies = companies;
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<CompanyPageDto> listCompanies(String cursor, Integer limit, String q, String sort) {
		CompanySort order = CompanySort.fromParameter(sort == null ? "name" : sort)
			.orElseThrow(() -> new InvalidParameterException("Ordenación no admitida: " + sort));
		int pageSize = limit == null ? 25 : limit;
		Window<Company> window = companies.list(q, order, pageSize, CompanyCursor.decode(cursor, order));

		var pageInfo = new PageInfoDto(window.hasNext());
		if (window.hasNext() && !window.isEmpty()) {
			var last = (KeysetScrollPosition) window.positionAt(window.size() - 1);
			pageInfo.setNextCursor(CompanyCursor.encode(order, last));
		}
		var page = new CompanyPageDto(window.getContent().stream().map(CompaniesController::toDto).toList(), pageInfo);
		return ResponseEntity.ok(page);
	}

	@Override
	@RequiresPermission(Permission.COMPANY_EDIT)
	public ResponseEntity<CompanyDto> createCompany(CompanyCreateDto body, UUID idempotencyKey) {
		// Idempotency-Key lo trata IdempotencyFilter antes de llegar aquí (paso 1.6).
		Company company = companies.create(body.getName(), body.getTaxId());
		return ResponseEntity.created(URI.create(COMPANIES_PATH + company.id()))
			.eTag(etag(company))
			.body(toDto(company));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<CompanyDto> getCompany(UUID companyId) {
		Company company = companies.get(companyId);
		return ResponseEntity.ok().eTag(etag(company)).body(toDto(company));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<AuditEventPageDto> listCompanyHistory(UUID companyId, String cursor, Integer limit) {
		var page = companies.history(companyId, AuditEvents.before(cursor), limit == null ? 25 : limit);
		return ResponseEntity.ok(AuditEvents.toPage(page));
	}

	private static String etag(Company company) {
		return ETags.of(company.version());
	}

	static CompanyDto toDto(Company company) {
		return new CompanyDto(company.id(), company.name(), company.taxId().value(),
				company.createdAt().atOffset(ZoneOffset.UTC), company.updatedAt().atOffset(ZoneOffset.UTC));
	}

}
