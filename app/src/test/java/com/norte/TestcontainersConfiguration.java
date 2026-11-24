package com.norte;

import com.norte.support.IntegrationTest.TestDatabase;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL con los mismos roles que en local: Flyway migra como dueño y la aplicación conecta sin privilegios.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String POSTGRES_IMAGE = "postgres:18-alpine";

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
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

	@Bean
	TestDatabase testDatabase(PostgreSQLContainer postgres) {
		var owner = new DriverManagerDataSource(postgres.getJdbcUrl(), "norte_owner", "norte_owner");
		return new TestDatabase(new JdbcTemplate(owner));
	}

}
