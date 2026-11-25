package com.norte.shared.api.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
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
