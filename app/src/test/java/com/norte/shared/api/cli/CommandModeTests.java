package com.norte.shared.api.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.norte.TestcontainersConfiguration;
import com.norte.shared.application.TenantProvisioning;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Los comandos de administración arrancan la aplicación sin servidor web (paso 1.1): nada de la configuración web
 * (seguridad, interceptores) puede ser obligatorio para que el contexto arranque.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class CommandModeTests {

	@Autowired
	TenantProvisioning provisioning;

	@Autowired
	ProvisionTenantCommand command;

	@Test
	void contextStartsWithoutAWebServer() {
		assertThat(provisioning).isNotNull();
		assertThat(command).isNotNull();
	}

}
