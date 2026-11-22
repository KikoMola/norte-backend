package com.norte.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Backend for Frontend (ADR 0.2): el navegador solo tiene una cookie de sesión HttpOnly y los tokens no llegan nunca
 * a JavaScript. El BFF hace el login OIDC contra Keycloak y reenvía las llamadas a /api con el access token.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BffApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffApplication.class, args);
	}

}
