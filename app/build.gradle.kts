plugins {
	java
	jacoco
	id("org.springframework.boot")
	id("io.spring.dependency-management")
	id("org.openapi.generator")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "2.2.0"

val archunitVersion = "1.5.1"
val testcontainersKeycloakVersion = "4.4.1"
val jqwikVersion = "1.10.2"
val awsSdkVersion = "2.59.9"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	// Email con plantillas (paso 1.8). Thymeleaf servirá también para el PDF de la factura.
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	// Solo el motor, sin el starter: la API no devuelve vistas y el motor propio resuelve HTML y texto.
	implementation("org.thymeleaf:thymeleaf-spring6")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	// Historial de cambios (paso 1.4). Versión gestionada por Spring Boot, la misma que Hibernate ORM.
	implementation("org.hibernate.orm:hibernate-envers")
	// Ficheros en un almacén compatible con S3 (paso 1.7). Solo el cliente síncrono con el HTTP del JDK: sin Netty ni
	// Apache HttpClient, que no hacen falta.
	implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
	implementation("software.amazon.awssdk:s3") {
		exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
		exclude(group = "software.amazon.awssdk", module = "apache-client")
	}
	implementation("software.amazon.awssdk:url-connection-client")
	implementation("org.springframework.modulith:spring-modulith-observability-api")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	runtimeOnly("org.springframework.modulith:spring-modulith-observability-core")
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
	testImplementation("com.github.dasniko:testcontainers-keycloak:$testcontainersKeycloakVersion")
	testImplementation("net.jqwik:jqwik:$jqwikVersion")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

// Contract first (paso 0.23): las interfaces de Spring y los DTO se generan desde api/openapi.yaml en cada build.
val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
	generatorName = "spring"
	inputSpec = rootProject.file("api/openapi.yaml")
	outputDir = generatedOpenApiDir
	apiPackage = "com.norte.contract.api"
	modelPackage = "com.norte.contract.model"
	modelNameSuffix = "Dto"
	cleanupOutput = true
	globalProperties = mapOf("apis" to "", "models" to "", "supportingFiles" to "", "modelDocs" to "false", "apiDocs" to "false")
	configOptions = mapOf(
		"interfaceOnly" to "true",
		"skipDefaultInterface" to "true",
		"useTags" to "true",
		"useSpringBoot3" to "true",
		"useJakartaEe" to "true",
		"useBeanValidation" to "true",
		"useResponseEntity" to "true",
		"openApiNullable" to "false",
		"documentationProvider" to "none",
		"annotationLibrary" to "none",
		"dateLibrary" to "java8",
		"sourceFolder" to "src/main/java",
		"hideGenerationTimestamp" to "true",
	)
}

sourceSets {
	main {
		java.srcDir(generatedOpenApiDir.map { it.dir("src/main/java") })
	}
	test {
		// El realm de Keycloak de infra/ se usa también en los tests de seguridad.
		resources.srcDir(rootProject.file("infra/keycloak"))
	}
}

tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}

tasks.withType<Test> {
	// JUnit Jupiter, ArchUnit y jqwik (tests de propiedades, paso 1.5) corren en la misma plataforma.
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
	classDirectories.setFrom(files(classDirectories.files.map {
		fileTree(it) { exclude("com/norte/contract/**") }
	}))
}

// La consola de Windows no usa UTF-8 por defecto: sin esto, los mensajes de los comandos salen con los acentos rotos.
tasks.bootRun {
	jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
