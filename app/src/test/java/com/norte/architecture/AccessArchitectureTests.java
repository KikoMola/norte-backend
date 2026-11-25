package com.norte.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.norte.shared.api.security.RequiresPermission;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.web.bind.annotation.RestController;

/**
 * Ningún endpoint queda sin permiso por olvido (paso 1.3): todo método público de un controlador lleva
 * {@link RequiresPermission}. La única excepción es la sesión ({@code GET /me}), que puede leer cualquier usuario.
 */
@AnalyzeClasses(packages = "com.norte", importOptions = ImportOption.DoNotIncludeTests.class)
class AccessArchitectureTests {

	@ArchTest
	static final ArchRule everyEndpointRequiresAPermission = methods().that()
		.areDeclaredInClassesThat()
		.areAnnotatedWith(RestController.class)
		.and()
		.areDeclaredInClassesThat()
		.doNotHaveSimpleName("SessionController")
		.and()
		.arePublic()
		.should()
		.beAnnotatedWith(RequiresPermission.class)
		.as("todo endpoint declara el permiso que exige con @RequiresPermission");

}
