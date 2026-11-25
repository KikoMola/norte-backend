package com.norte;

import com.norte.support.IntegrationTest.TestDatabase;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL con los mismos roles que en local: Flyway migra como dueño y la aplicación conecta sin privilegios.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final String POSTGRES_IMAGE = "postgres:18-alpine";

	public static final String SEAWEEDFS_IMAGE = "chrislusf/seaweedfs:4.55";

	static final int S3_PORT = 8333;

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

	/** Almacén de ficheros S3 (paso 1.7), con las mismas credenciales que en local. */
	@Bean
	GenericContainer<?> seaweedfsContainer() {
		return new GenericContainer<>(DockerImageName.parse(SEAWEEDFS_IMAGE))
			.withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json")
			.withCopyFileToContainer(MountableFile.forHostPath("../infra/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json")
			.withExposedPorts(S3_PORT)
			// Sin credenciales responde 403: la API S3 ya está atendiendo.
			.waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403));
	}

	@Bean
	DynamicPropertyRegistrar filesProperties(GenericContainer<?> seaweedfsContainer) {
		return registry -> registry.add("norte.files.endpoint",
				() -> "http://" + seaweedfsContainer.getHost() + ":" + seaweedfsContainer.getMappedPort(S3_PORT));
	}

	@Bean
	TestDatabase testDatabase(PostgreSQLContainer postgres) {
		return new TestDatabase(postgres.getJdbcUrl());
	}

}
