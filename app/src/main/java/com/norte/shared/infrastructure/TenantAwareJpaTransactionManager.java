package com.norte.shared.infrastructure;

import java.sql.PreparedStatement;
import java.util.UUID;

import com.norte.shared.application.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;

import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Fija el tenant en PostgreSQL al empezar cada transacción con {@code set_config('app.tenant_id', ?, true)}, el
 * equivalente a {@code SET LOCAL}: dura solo la transacción y no se filtra a otras peticiones a través del pool.
 *
 * <p>Las políticas RLS comparan {@code tenant_id} con ese valor (paso 0.16). Sin tenant no se fija nada y las tablas
 * con RLS no devuelven ni aceptan filas.
 */
class TenantAwareJpaTransactionManager extends JpaTransactionManager {

	static final String SET_TENANT_SQL = "select set_config('app.tenant_id', ?, true)";

	TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
		super(entityManagerFactory);
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		super.doBegin(transaction, definition);
		TenantContext.currentTenantId().ifPresent(this::setTenant);
	}

	private void setTenant(UUID tenantId) {
		EntityManager entityManager = EntityManagerFactoryUtils
			.getTransactionalEntityManager(obtainEntityManagerFactory());
		if (entityManager == null) {
			throw new IllegalStateException("No transactional EntityManager after transaction begin");
		}
		entityManager.unwrap(Session.class).doWork(connection -> {
			try (PreparedStatement statement = connection.prepareStatement(SET_TENANT_SQL)) {
				statement.setString(1, tenantId.toString());
				statement.execute();
			}
		});
	}

}
