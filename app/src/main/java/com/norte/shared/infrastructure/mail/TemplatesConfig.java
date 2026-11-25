package com.norte.shared.infrastructure.mail;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Motor de plantillas de correos y documentos (paso 1.8), en {@code src/main/resources/templates}. Cada plantilla se
 * pide con su extensión: {@code .html} se procesa como HTML (escapa lo que se pinta) y {@code .txt} como texto.
 */
@Configuration(proxyBeanMethods = false)
class TemplatesConfig {

	@Bean
	SpringTemplateEngine templateEngine(MessageSource messages) {
		var engine = new SpringTemplateEngine();
		engine.setTemplateEngineMessageSource(messages);
		engine.addTemplateResolver(resolver("*.html", TemplateMode.HTML, 1));
		engine.addTemplateResolver(resolver("*.txt", TemplateMode.TEXT, 2));
		return engine;
	}

	private static ClassLoaderTemplateResolver resolver(String pattern, TemplateMode mode, int order) {
		var resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setResolvablePatterns(Set.of(pattern));
		resolver.setTemplateMode(mode);
		resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
		resolver.setCheckExistence(true);
		resolver.setOrder(order);
		return resolver;
	}

}
