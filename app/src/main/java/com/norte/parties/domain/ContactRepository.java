package com.norte.parties.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Contactos; RLS filtra por tenant. */
public interface ContactRepository extends JpaRepository<Contact, UUID> {

	List<Contact> findByCompanyIdOrderByFirstNameAscLastNameAscIdAsc(UUID companyId);

	Optional<Contact> findByIdAndCompanyId(UUID id, UUID companyId);

	long countByCompanyId(UUID companyId);

}
