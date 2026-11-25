package com.norte.shared.infrastructure.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.norte.shared.application.mail.Mail;
import com.norte.shared.application.mail.MailDeliveryException;
import com.norte.shared.application.mail.Mailer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * {@link Mailer} por SMTP con plantillas Thymeleaf: {@code mail/<plantilla>.html} y {@code mail/<plantilla>.txt}, las
 * dos con los textos de {@code i18n/mail*.properties}. Cada correo lleva versión HTML y de texto: hay clientes que
 * solo enseñan la de texto, y los filtros de spam desconfían de los correos solo HTML.
 */
@Component
class SmtpMailer implements Mailer {

	private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);

	private final JavaMailSender sender;

	private final ITemplateEngine templates;

	private final String from;

	SmtpMailer(JavaMailSender sender, ITemplateEngine templates, @Value("${norte.mail.from}") String from) {
		this.sender = sender;
		this.templates = templates;
		this.from = from;
	}

	@Override
	public void send(Mail mail) {
		var context = new Context(mail.locale(), mail.variables());
		// El marco común de los correos firma con el nombre del remitente.
		context.setVariable("senderName", mail.senderName());
		String html = templates.process("mail/" + mail.template() + ".html", context);
		String text = templates.process("mail/" + mail.template() + ".txt", context);
		try {
			var message = sender.createMimeMessage();
			var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(new InternetAddress(from, mail.senderName(), StandardCharsets.UTF_8.name()));
			helper.setTo(mail.to().value());
			if (mail.replyTo().isPresent()) {
				helper.setReplyTo(mail.replyTo().get().value());
			}
			helper.setSubject(subjectOf(html, mail.template()));
			helper.setText(text, html);
			for (Mail.Attachment attachment : mail.attachments()) {
				helper.addAttachment(attachment.filename(), new ByteArrayResource(attachment.content()),
						attachment.contentType());
			}
			sender.send(message);
		}
		catch (MessagingException | UnsupportedEncodingException | MailException ex) {
			throw new MailDeliveryException("Could not send mail '" + mail.template() + "'", ex);
		}
	}

	/** El asunto es el {@code <title>} de la versión HTML, así se traduce como el resto de la plantilla. */
	static String subjectOf(String html, String template) {
		Matcher title = TITLE.matcher(html);
		if (!title.find() || title.group(1).isBlank()) {
			throw new IllegalStateException("Mail template '" + template + "' has no <title>");
		}
		return HtmlUtils.htmlUnescape(title.group(1).strip().replaceAll("\\s+", " "));
	}

}
