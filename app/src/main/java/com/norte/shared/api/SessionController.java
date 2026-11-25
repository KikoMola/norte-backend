package com.norte.shared.api;

import java.util.List;

import com.norte.contract.api.SessionApi;
import com.norte.contract.model.BusinessModuleDto;
import com.norte.contract.model.MeDto;
import com.norte.shared.application.AccessService;
import com.norte.shared.application.CurrentUser;
import com.norte.shared.application.TenantContext;
import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.Permission;
import com.norte.shared.domain.access.TenantAccess;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController implements SessionApi {

	private final AccessService accessService;

	SessionController(AccessService accessService) {
		this.accessService = accessService;
	}

	/** Sin {@code RequiresPermission}: cualquier usuario autenticado puede saber quién es y qué puede hacer. */
	@Override
	public ResponseEntity<MeDto> getMe() {
		CurrentUser user = TenantContext.currentUser()
			.orElseThrow(() -> new IllegalStateException("Authenticated request without user"));
		TenantAccess access = accessService.current();
		List<String> permissions = access.permissions().stream().map(Permission::code).sorted().toList();
		List<BusinessModuleDto> modules = access.modules()
			.stream()
			.sorted()
			.map(BusinessModule::code)
			.map(BusinessModuleDto::fromValue)
			.toList();
		var me = new MeDto(user.id(), user.username(), user.tenantId(), user.roles().stream().sorted().toList(),
				permissions, modules);
		me.setName(user.name());
		me.setEmail(user.email());
		return ResponseEntity.ok(me);
	}

}
