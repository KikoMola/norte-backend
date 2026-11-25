package com.norte.parties.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Las consultas no filtran por tenant: lo hace PostgreSQL con RLS a partir del {@code app.tenant_id} de la
 * transacción.
 */
public interface CompanyRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {

	boolean existsByTaxId(String taxId);

	boolean existsByTaxIdAndIdNot(String taxId, UUID id);

	/**
	 * Posibles duplicados de un alta o edición (paso 1.10): mismo NIF o nombre parecido (pg_trgm). word_similarity
	 * compara el texto buscado con el trozo más parecido del nombre, así «Acme» encuentra «Acme Ibérica S.L.». Los de
	 * mismo NIF van primero.
	 * @param excludedId la propia empresa al editar; cualquier UUID que no exista al dar de alta
	 */
	@Query(value = """
			select * from parties.company
			where id <> :excludedId
			  and (tax_id = :taxId or word_similarity(lower(:name), lower(name)) >= 0.6)
			order by (tax_id = :taxId) desc, word_similarity(lower(:name), lower(name)) desc, name
			limit 5""", nativeQuery = true)
	List<Company> findPossibleDuplicates(String name, String taxId, UUID excludedId);

}
