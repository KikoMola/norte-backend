package com.norte.shared.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Solo con servidor web: los comandos de administración (provision-tenant) arrancan sin él.
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AccessWebConfig implements WebMvcConfigurer {

	private final AccessInterceptor accessInterceptor;

	AccessWebConfig(AccessInterceptor accessInterceptor) {
		this.accessInterceptor = accessInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(accessInterceptor);
	}

}
