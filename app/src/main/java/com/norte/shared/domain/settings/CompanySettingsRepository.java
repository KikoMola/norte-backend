package com.norte.shared.domain.settings;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Ajustes de empresa; RLS deja ver solo los del tenant actual, así que hay como mucho uno. */
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

}
