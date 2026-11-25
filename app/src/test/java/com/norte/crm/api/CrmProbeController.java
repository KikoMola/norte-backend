package com.norte.crm.api;

import com.norte.shared.api.security.RequiresPermission;
import com.norte.shared.domain.access.Permission;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo en tests: un endpoint en el paquete del módulo CRM para comprobar que con el módulo inactivo responde 403
 * {@code module.disabled} (paso 1.2), antes de que el CRM tenga endpoints propios.
 */
@RestController
class CrmProbeController {

	@GetMapping("/test-only/crm-probe")
	@RequiresPermission(Permission.COMPANY_READ)
	String probe() {
		return "ok";
	}

}
