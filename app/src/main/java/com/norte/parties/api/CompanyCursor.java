package com.norte.parties.api;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.norte.parties.application.CompanySort;
import com.norte.shared.api.problem.InvalidParameterException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

/**
 * Cursor opaco de paginación: JSON en Base64 URL-safe con la ordenación y los valores de la última fila de la página
 * ({@code {"s":"name","v":"Acme","id":"…"}}). El cliente no debe interpretarlo.
 */
final class CompanyCursor {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private CompanyCursor() {
	}

	static String encode(CompanySort sort, KeysetScrollPosition position) {
		Map<String, Object> keys = position.getKeys();
		var payload = new LinkedHashMap<String, Object>();
		payload.put("s", sort.parameter());
		payload.put("v", String.valueOf(keys.get(sort.property())));
		payload.put("id", String.valueOf(keys.get("id")));
		byte[] json = JSON.writeValueAsBytes(payload);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
	}

	/** Sin cursor, primera página. Un cursor manipulado o de otra ordenación es un 400. */
	static ScrollPosition decode(String cursor, CompanySort sort) {
		if (cursor == null || cursor.isBlank()) {
			return ScrollPosition.keyset();
		}
		try {
			JsonNode node = JSON.readTree(Base64.getUrlDecoder().decode(cursor));
			if (!sort.parameter().equals(node.path("s").asString())) {
				throw invalid();
			}
			Object value = switch (sort.property()) {
				case "createdAt" -> Instant.parse(node.path("v").asString());
				default -> node.path("v").asString();
			};
			var keys = new LinkedHashMap<String, Object>();
			keys.put(sort.property(), value);
			keys.put("id", UUID.fromString(node.path("id").asString()));
			return ScrollPosition.forward(keys);
		}
		catch (IllegalArgumentException | JacksonException | java.time.format.DateTimeParseException ex) {
			throw invalid();
		}
	}

	private static InvalidParameterException invalid() {
		return new InvalidParameterException("El cursor no es válido para esta consulta.");
	}


}
