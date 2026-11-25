package com.norte.shared.api;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.norte.contract.api.SettingsApi;
import com.norte.contract.model.AuditEventPageDto;
import com.norte.contract.model.CompanySettingsDto;
import com.norte.contract.model.CompanySettingsUpdateDto;
import com.norte.contract.model.PostalAddressDto;
import com.norte.shared.api.security.RequiresPermission;
import com.norte.shared.application.settings.CompanySettingsService;
import com.norte.shared.domain.access.Permission;
import com.norte.shared.domain.settings.CompanySettings;
import com.norte.shared.domain.settings.CompanySettings.Details;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Ajustes de la empresa del tenant (paso 1.9). */
@RestController
class SettingsController implements SettingsApi {

	private final CompanySettingsService settings;

	SettingsController(CompanySettingsService settings) {
		this.settings = settings;
	}

	@Override
	@RequiresPermission(Permission.SETTINGS_READ)
	public ResponseEntity<CompanySettingsDto> getCompanySettings() {
		return response(settings.current());
	}

	@Override
	@RequiresPermission(Permission.SETTINGS_EDIT)
	public ResponseEntity<CompanySettingsDto> updateCompanySettings(String ifMatch, CompanySettingsUpdateDto body) {
		return response(settings.update(toDetails(body), ETags.parse(ifMatch)));
	}

	@Override
	@RequiresPermission(Permission.SETTINGS_EDIT)
	public ResponseEntity<CompanySettingsDto> changeCompanyLogo(String ifMatch, MultipartFile file) {
		try {
			return response(settings.changeLogo(file.getBytes(), ETags.parse(ifMatch)));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	@RequiresPermission(Permission.SETTINGS_EDIT)
	public ResponseEntity<CompanySettingsDto> removeCompanyLogo(String ifMatch) {
		return response(settings.removeLogo(ETags.parse(ifMatch)));
	}

	@Override
	@RequiresPermission(Permission.SETTINGS_READ)
	public ResponseEntity<AuditEventPageDto> listCompanySettingsHistory(String cursor, Integer limit) {
		return ResponseEntity
			.ok(AuditEvents.toPage(settings.history(AuditEvents.before(cursor), limit == null ? 25 : limit)));
	}

	private ResponseEntity<CompanySettingsDto> response(CompanySettings company) {
		return ResponseEntity.ok().eTag(ETags.of(company.version())).body(toDto(company));
	}

	private CompanySettingsDto toDto(CompanySettings company) {
		Details details = company.details();
		var address = new PostalAddressDto(details.country());
		address.setLine1(details.addressLine1());
		address.setLine2(details.addressLine2());
		address.setPostalCode(details.postalCode());
		address.setCity(details.city());
		address.setProvince(details.province());
		var dto = new CompanySettingsDto(details.legalName(), address, details.fiscalYearStartMonth(),
				details.timeZone(), CompanySettingsDto.LanguageEnum.fromValue(details.language()));
		dto.setTradeName(details.tradeName());
		dto.setTaxId(details.taxId());
		dto.setEmail(details.email());
		dto.setPhone(details.phone());
		dto.setWebsite(details.website());
		settings.logoUrl(company).ifPresent(dto::setLogoUrl);
		return dto;
	}

	private static Details toDetails(CompanySettingsUpdateDto body) {
		PostalAddressDto address = body.getAddress();
		return new Details(body.getLegalName(), body.getTradeName(), body.getTaxId(), address.getLine1(),
				address.getLine2(), address.getPostalCode(), address.getCity(), address.getProvince(),
				address.getCountry(), body.getEmail(), body.getPhone(), body.getWebsite(),
				body.getFiscalYearStartMonth(), body.getTimeZone(), body.getLanguage().getValue());
	}

}
