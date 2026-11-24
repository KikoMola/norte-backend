package com.norte.shared.api.problem;

/** Un parámetro de la petición (query, cabecera o cursor) no es válido: 400 {@code request.invalid_parameter}. */
public class InvalidParameterException extends RuntimeException {

	public InvalidParameterException(String message) {
		super(message);
	}

}
