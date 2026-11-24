package com.norte.parties.application;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.data.domain.Sort;

/** Ordenaciones admitidas en el listado de empresas; el {@code id} desempata para que el cursor sea estable. */
public enum CompanySort {

	NAME_ASC("name", Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))),
	NAME_DESC("-name", Sort.by(Sort.Order.desc("name"), Sort.Order.desc("id"))),
	CREATED_ASC("createdAt", Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))),
	CREATED_DESC("-createdAt", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

	private final String parameter;

	private final Sort sort;

	CompanySort(String parameter, Sort sort) {
		this.parameter = parameter;
		this.sort = sort;
	}

	public static Optional<CompanySort> fromParameter(String parameter) {
		return Arrays.stream(values()).filter(value -> value.parameter.equals(parameter)).findFirst();
	}

	public String parameter() {
		return parameter;
	}

	public Sort sort() {
		return sort;
	}

	/** Propiedad principal de la ordenación ({@code name} o {@code createdAt}). */
	public String property() {
		return sort.iterator().next().getProperty();
	}

}
