package com.norte.shared.api.cli;

/** Comandos de administración que se lanzan con la misma imagen de la aplicación. */
public final class Commands {

	private Commands() {
	}

	/** Si el primer argumento es un comando, la aplicación arranca sin servidor web y termina al acabar. */
	public static boolean isCommand(String... args) {
		return ProvisionTenantCommand.isRequested(args);
	}

}
