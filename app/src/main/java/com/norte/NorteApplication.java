package com.norte;

import com.norte.shared.api.cli.Commands;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class NorteApplication {

	public static void main(String[] args) {
		var application = new SpringApplication(NorteApplication.class);
		boolean command = Commands.isCommand(args);
		if (command) {
			// Comandos de administración (alta de tenant…): sin servidor web y terminan al acabar.
			application.setWebApplicationType(WebApplicationType.NONE);
		}
		ConfigurableApplicationContext context = application.run(args);
		if (command) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
