package com.norte.parties.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Las consultas no filtran por tenant: lo hace PostgreSQL con RLS a partir del {@code app.tenant_id} de la
 * transacción.
 */
public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

	boolean existsByTaxId(String taxId);

}
