package com.norte.parties.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.norte.shared.domain.TaxId;
import org.junit.jupiter.api.Test;

class CompanyTests {

	private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final Instant NOW = Instant.parse("2026-11-24T09:30:00Z");

	@Test
	void createsCompanyWithTrimmedNameAndNormalizedTaxId() {
		Company company = Company.create(TENANT, "  Acme Ibérica S.L. ", TaxId.of("b12345674"), NOW);

		assertThat(company.id()).isNotNull();
		assertThat(company.tenantId()).isEqualTo(TENANT);
		assertThat(company.name()).isEqualTo("Acme Ibérica S.L.");
		assertThat(company.taxId().value()).isEqualTo("B12345674");
		assertThat(company.createdAt()).isEqualTo(NOW);
		assertThat(company.updatedAt()).isEqualTo(NOW);
		assertThat(company.version()).isZero();
	}

	@Test
	void rejectsBlankName() {
		assertThatThrownBy(() -> Company.create(TENANT, "   ", TaxId.of("B12345674"), NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNameLongerThanLimit() {
		String longName = "A".repeat(Company.NAME_MAX_LENGTH + 1);
		assertThatThrownBy(() -> Company.create(TENANT, longName, TaxId.of("B12345674"), NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void requiresTenant() {
		assertThatThrownBy(() -> Company.create(null, "Acme", TaxId.of("B12345674"), NOW))
			.isInstanceOf(NullPointerException.class);
	}

}
