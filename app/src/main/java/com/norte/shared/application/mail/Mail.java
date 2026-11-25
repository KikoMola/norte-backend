package com.norte.shared.application.mail;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.norte.shared.domain.Email;

/**
 * Correo a enviar con una plantilla (paso 1.8).
 *
 * @param template nombre de la plantilla, {@code templates/mail/<template>.html} y {@code .txt}; su asunto es el
 * {@code <title>} de la versión HTML
 * @param variables datos de la plantilla, ya formateados para el idioma (importes, fechas)
 * @param senderName nombre visible del remitente, normalmente el del tenant; la dirección es siempre la de Norte
 * @param replyTo a quién van las respuestas, normalmente el correo del tenant
 */
public record Mail(Email to, String template, Locale locale, Map<String, Object> variables, String senderName,
		Optional<Email> replyTo, List<Attachment> attachments) {

	public Mail {
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(template, "template");
		Objects.requireNonNull(locale, "locale");
		Objects.requireNonNull(senderName, "senderName");
		variables = Map.copyOf(variables);
		replyTo = Objects.requireNonNull(replyTo, "replyTo");
		attachments = List.copyOf(attachments);
	}

	public record Attachment(String filename, String contentType, byte[] content) {

		public Attachment {
			Objects.requireNonNull(filename, "filename");
			Objects.requireNonNull(contentType, "contentType");
			Objects.requireNonNull(content, "content");
		}

	}

}
