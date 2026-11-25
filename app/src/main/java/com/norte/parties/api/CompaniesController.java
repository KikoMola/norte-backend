package com.norte.parties.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import com.norte.contract.api.CompaniesApi;
import com.norte.contract.model.AuditEventPageDto;
import com.norte.contract.model.CompanyCreateDto;
import com.norte.contract.model.CompanyDto;
import com.norte.contract.model.CompanyDuplicateListDto;
import com.norte.contract.model.CompanyDuplicateListItemsInnerDto;
import com.norte.contract.model.CompanyPageDto;
import com.norte.contract.model.CompanyUpdateDto;
import com.norte.contract.model.ContactDto;
import com.norte.contract.model.ContactInputDto;
import com.norte.contract.model.ContactListDto;
import com.norte.contract.model.ContactRoleDto;
import com.norte.contract.model.MoneyDto;
import com.norte.contract.model.PageInfoDto;
import com.norte.contract.model.PostalAddressDto;
import com.norte.parties.application.CompanyService;
import com.norte.parties.application.CompanyService.CompanyInput;
import com.norte.parties.application.CompanyService.Duplicate;
import com.norte.parties.application.CompanySort;
import com.norte.parties.application.ContactService;
import com.norte.parties.application.ContactService.ContactInput;
import com.norte.parties.domain.Company;
import com.norte.parties.domain.Company.Address;
import com.norte.parties.domain.Company.Profile;
import com.norte.parties.domain.Contact;
import com.norte.shared.api.AuditEvents;
import com.norte.shared.api.ETags;
import com.norte.shared.api.problem.InvalidParameterException;
import com.norte.shared.api.security.RequiresPermission;
import com.norte.shared.domain.Money;
import com.norte.shared.domain.access.Permission;
import com.norte.shared.domain.error.ValidationException;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Empresas y sus contactos (pasos 0.22 y 1.10). */
@RestController
class CompaniesController implements CompaniesApi {

	private static final String COMPANIES_PATH = "/api/v1/companies/";

	private final CompanyService companies;

	private final ContactService contacts;

	CompaniesController(CompanyService companies, ContactService contacts) {
		this.companies = companies;
		this.contacts = contacts;
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
		Company company = companies.create(new CompanyInput(body.getName(), body.getTaxId(), body.getTradeName(),
				body.getEmail(), body.getPhone(), body.getWebsite(), toAddress(body.getAddress()),
				euros(body.getCreditLimit()), !Boolean.FALSE.equals(body.getIsCustomer()),
				Boolean.TRUE.equals(body.getIsSupplier())));
		return ResponseEntity.created(URI.create(COMPANIES_PATH + company.id()))
			.eTag(ETags.of(company.version()))
			.body(toDto(company));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<CompanyDto> getCompany(UUID companyId) {
		Company company = companies.get(companyId);
		return ResponseEntity.ok().eTag(ETags.of(company.version())).body(toDto(company));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_EDIT)
	public ResponseEntity<CompanyDto> updateCompany(String ifMatch, UUID companyId, CompanyUpdateDto body) {
		Company company = companies.update(companyId,
				new CompanyInput(body.getName(), body.getTaxId(), body.getTradeName(), body.getEmail(),
						body.getPhone(), body.getWebsite(), toAddress(body.getAddress()), euros(body.getCreditLimit()),
						body.getIsCustomer(), body.getIsSupplier()),
				ETags.parse(ifMatch));
		return ResponseEntity.ok().eTag(ETags.of(company.version())).body(toDto(company));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<CompanyDuplicateListDto> findCompanyDuplicates(String name, String taxId, UUID excludeId) {
		var items = companies.duplicates(name, taxId, excludeId)
			.stream()
			.map(duplicate -> new CompanyDuplicateListItemsInnerDto(toDto(duplicate.company()),
					duplicate.reason() == Duplicate.Reason.TAX_ID ? CompanyDuplicateListItemsInnerDto.ReasonEnum.TAX_ID
							: CompanyDuplicateListItemsInnerDto.ReasonEnum.SIMILAR_NAME))
			.toList();
		return ResponseEntity.ok(new CompanyDuplicateListDto(items));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<AuditEventPageDto> listCompanyHistory(UUID companyId, String cursor, Integer limit) {
		var page = companies.history(companyId, AuditEvents.before(cursor), limit == null ? 25 : limit);
		return ResponseEntity.ok(AuditEvents.toPage(page));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<ContactListDto> listCompanyContacts(UUID companyId) {
		return ResponseEntity
			.ok(new ContactListDto(contacts.list(companyId).stream().map(CompaniesController::toDto).toList()));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_EDIT)
	public ResponseEntity<ContactDto> createCompanyContact(UUID companyId, ContactInputDto body, UUID idempotencyKey) {
		Contact contact = contacts.create(companyId, toInput(body));
		return ResponseEntity.created(URI.create(COMPANIES_PATH + companyId + "/contacts/" + contact.id()))
			.eTag(ETags.of(contact.version()))
			.body(toDto(contact));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_READ)
	public ResponseEntity<ContactDto> getCompanyContact(UUID companyId, UUID contactId) {
		Contact contact = contacts.get(companyId, contactId);
		return ResponseEntity.ok().eTag(ETags.of(contact.version())).body(toDto(contact));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_EDIT)
	public ResponseEntity<ContactDto> updateCompanyContact(String ifMatch, UUID companyId, UUID contactId,
			ContactInputDto body) {
		Contact contact = contacts.update(companyId, contactId, toInput(body), ETags.parse(ifMatch));
		return ResponseEntity.ok().eTag(ETags.of(contact.version())).body(toDto(contact));
	}

	@Override
	@RequiresPermission(Permission.COMPANY_EDIT)
	public ResponseEntity<Void> deleteCompanyContact(UUID companyId, UUID contactId) {
		contacts.delete(companyId, contactId);
		return ResponseEntity.noContent().build();
	}

	static CompanyDto toDto(Company company) {
		Profile profile = company.profile();
		Address address = profile.address();
		var addressDto = new PostalAddressDto(address.country());
		addressDto.setLine1(address.line1());
		addressDto.setLine2(address.line2());
		addressDto.setPostalCode(address.postalCode());
		addressDto.setCity(address.city());
		addressDto.setProvince(address.province());
		var dto = new CompanyDto(company.id(), company.name(), company.taxId().value(), addressDto, profile.customer(),
				profile.supplier(), company.createdAt().atOffset(ZoneOffset.UTC),
				company.updatedAt().atOffset(ZoneOffset.UTC));
		dto.setTradeName(profile.tradeName());
		dto.setEmail(profile.email());
		dto.setPhone(profile.phone());
		dto.setWebsite(profile.website());
		company.creditLimit()
			.ifPresent(limit -> dto.setCreditLimit(new MoneyDto(Money.of(limit, Money.EUR).toPlainString(), "EUR")));
		return dto;
	}

	private static ContactDto toDto(Contact contact) {
		Contact.Details details = contact.details();
		var dto = new ContactDto(details.firstName(), contact.id(), contact.companyId(),
				contact.createdAt().atOffset(ZoneOffset.UTC), contact.updatedAt().atOffset(ZoneOffset.UTC));
		dto.setLastName(details.lastName());
		dto.setJobTitle(details.jobTitle());
		dto.setEmail(details.email());
		dto.setPhone(details.phone());
		details.role().ifPresent(role -> dto.setRole(ContactRoleDto.fromValue(role.code())));
		return dto;
	}

	private static ContactInput toInput(ContactInputDto body) {
		return new ContactInput(body.getFirstName(), body.getLastName(), body.getJobTitle(), body.getEmail(),
				body.getPhone(), body.getRole() == null ? null : body.getRole().getValue());
	}

	private static Address toAddress(PostalAddressDto address) {
		if (address == null) {
			return Address.spain();
		}
		return new Address(address.getLine1(), address.getLine2(), address.getPostalCode(), address.getCity(),
				address.getProvince(), address.getCountry());
	}

	/** El límite de crédito va en euros; otra moneda es un 422. */
	private static BigDecimal euros(MoneyDto money) {
		if (money == null) {
			return null;
		}
		if (!"EUR".equals(money.getCurrency())) {
			throw ValidationException.of("creditLimit.currency", "currency.unsupported",
					"El límite de crédito va en euros.");
		}
		return new BigDecimal(money.getAmount());
	}

}
