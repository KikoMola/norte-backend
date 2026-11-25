package com.norte.shared.infrastructure;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.norte.shared.domain.access.AccessRepository;
import com.norte.shared.domain.access.BusinessModule;
import com.norte.shared.domain.access.Permission;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccessRepository implements AccessRepository {

	private final JdbcClient jdbc;

	JdbcAccessRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<BusinessModule> enabledModules() {
		return jdbc.sql("select module from shared.tenant_module")
			.query(String.class)
			.stream()
			.map(BusinessModule::fromCode)
			.flatMap(Optional::stream)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(BusinessModule.class)));
	}

	@Override
	public Set<Permission> permissionsOf(Collection<String> roles) {
		if (roles.isEmpty()) {
			return Set.of();
		}
		return jdbc.sql("select distinct permission from shared.role_permission where role in (:roles)")
			.param("roles", roles)
			.query(String.class)
			.stream()
			.map(Permission::fromCode)
			.flatMap(Optional::stream)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
	}

}
