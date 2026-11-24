package com.norte.shared.api;

import java.util.List;

import com.norte.contract.api.SessionApi;
import com.norte.contract.model.MeDto;
import com.norte.shared.application.CurrentUser;
import com.norte.shared.application.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController implements SessionApi {

	@Override
	public ResponseEntity<MeDto> getMe() {
		CurrentUser user = TenantContext.currentUser()
			.orElseThrow(() -> new IllegalStateException("Authenticated request without user"));
		var me = new MeDto(user.id(), user.username(), user.tenantId(), List.copyOf(user.roles().stream().sorted().toList()));
		me.setName(user.name());
		me.setEmail(user.email());
		return ResponseEntity.ok(me);
	}

}
