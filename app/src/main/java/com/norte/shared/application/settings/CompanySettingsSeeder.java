package com.norte.shared.application.settings;

import java.time.Clock;
import java.util.UUID;

import com.norte.shared.application.TenantSeeder;
import com.norte.shared.domain.settings.CompanySettings;
import com.norte.shared.domain.settings.CompanySettingsRepository;

import org.springframework.stereotype.Component;

/** Ajustes iniciales en el alta de tenant (paso 1.9): la razón social es el nombre del tenant. Idempotente. */
@Component
class CompanySettingsSeeder implements TenantSeeder {

	private final CompanySettingsRepository settings;

	private final Clock clock;

	CompanySettingsSeeder(CompanySettingsRepository settings, Clock clock) {
		this.settings = settings;
		this.clock = clock;
	}

	@Override
	public void seed(UUID tenantId, String tenantName) {
		if (!settings.existsById(tenantId)) {
			settings.save(CompanySettings.initial(tenantId, tenantName, clock.instant()));
		}
	}

}
