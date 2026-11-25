package com.norte.shared.api;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;

import com.norte.contract.model.AuditEventActorDto;
import com.norte.contract.model.AuditEventChangesInnerDto;
import com.norte.contract.model.AuditEventDto;
import com.norte.contract.model.AuditEventPageDto;
import com.norte.contract.model.PageInfoDto;
import com.norte.shared.api.problem.InvalidParameterException;
import com.norte.shared.application.audit.History;

/**
 * Historial de cambios en el contrato (paso 1.4), común a todos los {@code GET /{recurso}/{id}/history}. El cursor
 * es la última revisión de la página, opaca para el cliente.
 */
public final class AuditEvents {

	private static final String CURSOR_PREFIX = "rev:";

	private AuditEvents() {
	}

	/** Sin cursor, desde el cambio más reciente. Un cursor manipulado es un 400. */
	public static OptionalLong before(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return OptionalLong.empty();
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!decoded.startsWith(CURSOR_PREFIX)) {
				throw invalidCursor();
			}
			return OptionalLong.of(Long.parseLong(decoded.substring(CURSOR_PREFIX.length())));
		}
		catch (IllegalArgumentException ex) {
			throw invalidCursor();
		}
	}

	public static AuditEventPageDto toPage(History.Page page) {
		var pageInfo = new PageInfoDto(page.next().isPresent());
		page.next().ifPresent(revision -> pageInfo.setNextCursor(cursor(revision)));
		return new AuditEventPageDto(page.events().stream().map(AuditEvents::toDto).toList(), pageInfo);
	}

	private static AuditEventDto toDto(History.Event event) {
		var dto = new AuditEventDto(Long.toString(event.revision()), event.occurredAt().atOffset(ZoneOffset.UTC),
				switch (event.kind()) {
					case CREATED -> AuditEventDto.ActionEnum.CREATED;
					case UPDATED -> AuditEventDto.ActionEnum.UPDATED;
					case DELETED -> AuditEventDto.ActionEnum.DELETED;
				},
				event.changes()
					.stream()
					.map(change -> new AuditEventChangesInnerDto(change.field(), change.from(), change.to()))
					.toList());
		event.author().ifPresent(author -> dto.setActor(new AuditEventActorDto(author.id(), author.name())));
		return dto;
	}

	private static String cursor(long revision) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString((CURSOR_PREFIX + revision).getBytes(StandardCharsets.UTF_8));
	}

	private static InvalidParameterException invalidCursor() {
		return new InvalidParameterException("El cursor no es válido para esta consulta.");
	}

}
