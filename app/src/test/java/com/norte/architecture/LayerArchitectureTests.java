package com.norte.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Capas dentro de cada módulo (paso 0.13):
 *
 * <pre>
 * api ──▶ application ──▶ domain ◀── infrastructure
 * </pre>
 *
 * {@code api} son los adaptadores web (controladores, errores, seguridad HTTP) y es la única capa que conoce los DTO
 * generados del contrato. {@code domain} no depende de nadie.
 */
@AnalyzeClasses(packages = "com.norte", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTests {

	private static final String DOMAIN = "com.norte.*.domain..";

	private static final String APPLICATION = "com.norte.*.application..";

	private static final String API = "com.norte.*.api..";

	private static final String INFRASTRUCTURE = "com.norte.*.infrastructure..";

	private static final String CONTRACT = "com.norte.contract..";

	@ArchTest
	static final ArchRule domainDependsOnNothingElse = noClasses().that()
		.resideInAPackage(DOMAIN)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(APPLICATION, API, INFRASTRUCTURE, CONTRACT)
		.as("domain no depende de application, api, infrastructure ni del contrato");

	@ArchTest
	static final ArchRule applicationDoesNotKnowAdapters = noClasses().that()
		.resideInAPackage(APPLICATION)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(API, INFRASTRUCTURE, CONTRACT)
		.as("application no depende de api, infrastructure ni del contrato");

	@ArchTest
	static final ArchRule infrastructureDoesNotKnowApi = noClasses().that()
		.resideInAPackage(INFRASTRUCTURE)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(API, CONTRACT)
		.as("infrastructure no depende de api ni del contrato");

	@ArchTest
	static final ArchRule apiDoesNotUseInfrastructure = noClasses().that()
		.resideInAPackage(API)
		.and()
		.resideOutsideOfPackage(CONTRACT)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(INFRASTRUCTURE)
		.as("api no depende de infrastructure");

	@ArchTest
	static final ArchRule controllersLiveInApi = classes().that()
		.areAnnotatedWith(RestController.class)
		.or()
		.areAnnotatedWith(RestControllerAdvice.class)
		.should()
		.resideInAPackage(API)
		.as("los controladores están en la capa api");

	@ArchTest
	static final ArchRule contractIsSelfContained = noClasses().that()
		.resideInAPackage(CONTRACT)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE)
		.as("el código generado del contrato no depende del resto");

}
