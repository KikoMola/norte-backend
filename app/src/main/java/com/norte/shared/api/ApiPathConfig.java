package com.norte.shared.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Las interfaces generadas del contrato declaran rutas relativas al servidor {@code /api/v1} de openapi.yaml; aquí se
 * añade ese prefijo a todos los controladores de Norte.
 */
@Configuration(proxyBeanMethods = false)
class ApiPathConfig implements WebMvcConfigurer {

	static final String API_PREFIX = "/api/v1";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX,
				HandlerTypePredicate.forBasePackage("com.norte").and(HandlerTypePredicate.forAnnotation(RestController.class)));
	}

}
