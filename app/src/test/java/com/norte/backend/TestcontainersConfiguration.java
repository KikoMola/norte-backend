package com.norte.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL con los mismos roles que en local: Flyway migra como dueño y la aplicación conecta sin privilegios.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
			.withDatabaseName("norte")
			.withCopyFileToContainer(MountableFile.forHostPath("../infra/postgres/init/01-roles.sql"),
					"/docker-entrypoint-initdb.d/01-roles.sql");
	}

	@Bean
	DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.datasource.username", () -> "norte_app");
			registry.add("spring.datasource.password", () -> "norte_app");
			registry.add("spring.flyway.user", () -> "norte_owner");
			registry.add("spring.flyway.password", () -> "norte_owner");
		};
	}

}
