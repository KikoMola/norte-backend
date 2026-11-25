package com.norte.shared.api.cli;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.norte.shared.application.IdentityDirectory.AdminUser;
import com.norte.shared.application.TenantProvisioning;
import com.norte.shared.domain.access.BusinessModule;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Comando de alta de tenant (paso 1.1). No hay endpoint: lo lanza un administrador del servidor.
 *
 * <pre>
 * java -jar app.jar provision-tenant --name="Acme Ibérica S.L." --admin-username=ana --admin-email=ana@acme.es \
 *     [--admin-first-name=Ana] [--admin-last-name=López] [--tenant-id=uuid] [--modules=crm,invoicing]
 * </pre>
 *
 * Es idempotente: repetirlo con los mismos datos no crea nada nuevo. Sin {@code --modules} se activan el CRM y la
 * facturación.
 */
@Component
class ProvisionTenantCommand implements ApplicationRunner, ExitCodeGenerator {

	static final String NAME = "provision-tenant";

	static final Set<BusinessModule> DEFAULT_MODULES = EnumSet.of(BusinessModule.CRM, BusinessModule.INVOICING);

	private final TenantProvisioning provisioning;

	private int exitCode;

	ProvisionTenantCommand(TenantProvisioning provisioning) {
		this.provisioning = provisioning;
	}

	/** Si los argumentos piden este comando: la aplicación arranca sin servidor web y termina al acabar. */
	public static boolean isRequested(String... args) {
		return args.length > 0 && NAME.equals(args[0]);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.getNonOptionArgs().contains(NAME)) {
			return;
		}
		try {
			String tenantId = optional(args, "tenant-id");
			String username = required(args, "admin-username");
			var admin = new AdminUser(username, required(args, "admin-email"),
					orDefault(args, "admin-first-name", username), orDefault(args, "admin-last-name", "Admin"));
			var result = provisioning.provision(tenantId == null ? null : UUID.fromString(tenantId),
					required(args, "name"), admin, modules(optional(args, "modules")));
			var out = System.out;
			out.println("Tenant:        " + result.tenantId());
			out.println("Organización:  " + result.organizationId());
			out.println("Administrador: " + admin.username());
			result.admin()
				.temporaryPassword()
				.ifPresentOrElse(password -> out.println("Contraseña temporal (se cambia en el primer acceso): " + password),
						() -> out.println("El administrador ya existía: se conserva su contraseña."));
		}
		catch (IllegalArgumentException ex) {
			System.err.println("provision-tenant: " + ex.getMessage());
			exitCode = 2;
		}
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	static Set<BusinessModule> modules(String value) {
		if (value == null) {
			return DEFAULT_MODULES;
		}
		return Arrays.stream(value.split(","))
			.map(String::strip)
			.filter(code -> !code.isEmpty())
			.map(code -> BusinessModule.fromCode(code)
				.orElseThrow(() -> new IllegalArgumentException("Módulo desconocido: " + code)))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(BusinessModule.class)));
	}

	private static String required(ApplicationArguments args, String name) {
		String value = optional(args, name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Falta --" + name);
		}
		return value;
	}

	private static String orDefault(ApplicationArguments args, String name, String fallback) {
		String value = optional(args, name);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String optional(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		return values == null || values.isEmpty() ? null : values.getFirst();
	}

}
