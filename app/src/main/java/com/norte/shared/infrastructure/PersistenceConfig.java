package com.norte.shared.infrastructure;

import java.time.Clock;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

	/** Sustituye al JpaTransactionManager de Boot para fijar el tenant de RLS en cada transacción. */
	@Bean
	JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		return new TenantAwareJpaTransactionManager(entityManagerFactory);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
