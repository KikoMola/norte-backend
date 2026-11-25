package com.norte.shared.api.security;

import java.util.Optional;

import com.norte.shared.application.AccessService;
import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.TenantAccess;
import com.norte.shared.domain.error.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Antes de cada endpoint comprueba, en este orden:
 *
 * <ol>
 * <li>Que el módulo del controlador (por su paquete, {@code com.norte.invoicing…}) está contratado: si no, 403
 * {@code module.disabled} (paso 1.2).
 * <li>Que el usuario tiene el permiso de {@link RequiresPermission}: si no, 403 {@code auth.forbidden} (paso 1.3).
 * </ol>
 *
 * El acceso se calcula una vez por petición y queda en el atributo {@link #ACCESS_ATTRIBUTE}.
 */
@Component
class AccessInterceptor implements HandlerInterceptor {

	static final String ACCESS_ATTRIBUTE = AccessInterceptor.class.getName() + ".access";

	private final AccessService accessService;

	AccessInterceptor(AccessService accessService) {
		this.accessService = accessService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		Optional<BusinessModule> module = BusinessModule.ofPackage(method.getBeanType().getPackageName());
		RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
		if (module.isEmpty() && required == null) {
			return true;
		}
		TenantAccess access = access(request);
		if (module.isPresent() && !access.hasModule(module.get())) {
			throw new ForbiddenException("module.disabled", "Tu organización no tiene contratado este módulo");
		}
		if (required != null && !access.can(required.value())) {
			throw new ForbiddenException("auth.forbidden", "No tienes permiso para esta acción");
		}
		return true;
	}

	private TenantAccess access(HttpServletRequest request) {
		if (request.getAttribute(ACCESS_ATTRIBUTE) instanceof TenantAccess cached) {
			return cached;
		}
		TenantAccess access = accessService.current();
		request.setAttribute(ACCESS_ATTRIBUTE, access);
		return access;
	}

}
