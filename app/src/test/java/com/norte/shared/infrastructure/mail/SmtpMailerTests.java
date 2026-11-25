package com.norte.shared.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.application.mail.Mail;
import com.norte.shared.application.mail.MailDeliveryException;
import com.norte.shared.application.mail.Mailer;
import com.norte.shared.domain.Email;
import com.norte.support.IntegrationTest;
import com.norte.support.MailpitContainer;
import org.junit.jupiter.api.Test;
import org.thymeleaf.ITemplateEngine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Correo con plantillas contra Mailpit real (paso 1.8). */
class SmtpMailerTests extends IntegrationTest {

	private static final byte[] PDF = "%PDF-1.7 factura".getBytes(StandardCharsets.UTF_8);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	Mailer mailer;

	@Autowired
	MailpitContainer mailpit;

	@Autowired
	ITemplateEngine templates;

	@Test
	void sendsTheInvoiceWithHtmlTextAndThePdf() throws Exception {
		Email to = uniqueRecipient();
		mailer.send(invoiceMail(to, Locale.forLanguageTag("es"), "Cerámicas del Turia S.L.", "25 dic 2026"));

		JsonNode summary = received(to);
		assertThat(summary.path("Subject").asString()).isEqualTo("Factura F-2026-0001 de Acme Ibérica S.L.");
		assertThat(summary.path("From").path("Name").asString()).isEqualTo("Acme Ibérica S.L.");
		assertThat(summary.path("From").path("Address").asString()).isEqualTo("no-reply@norte.localhost");

		JsonNode message = message(summary.path("ID").asString());
		assertThat(message.path("ReplyTo").get(0).path("Address").asString()).isEqualTo("facturas@acme.test");
		assertThat(message.path("HTML").asString()).contains("Hola, Cerámicas del Turia S.L.:")
			.contains("1.210,00 €")
			.contains("25 dic 2026")
			.contains("Este correo lo envía Acme Ibérica S.L. con Norte.");
		assertThat(message.path("Text").asString()).contains("Importe total: 1.210,00 €")
			.contains("Vencimiento: 25 dic 2026");
		JsonNode attachment = message.path("Attachments").get(0);
		assertThat(attachment.path("FileName").asString()).isEqualTo("F-2026-0001.pdf");
		assertThat(attachment.path("ContentType").asString()).isEqualTo("application/pdf");
	}

	@Test
	void usesTheLanguageOfTheMail() throws Exception {
		Email to = uniqueRecipient();
		mailer.send(invoiceMail(to, Locale.ENGLISH, "Globex", "Dec 25, 2026"));

		JsonNode summary = received(to);
		assertThat(summary.path("Subject").asString()).isEqualTo("Invoice F-2026-0001 from Acme Ibérica S.L.");
		assertThat(message(summary.path("ID").asString()).path("Text").asString()).contains("Hello Globex,");
	}

	@Test
	void escapesWhatTheUserWrote() throws Exception {
		Email to = uniqueRecipient();
		mailer.send(invoiceMail(to, Locale.forLanguageTag("es"), "<b>Malo</b>", null));

		JsonNode message = message(received(to).path("ID").asString());
		assertThat(message.path("HTML").asString()).contains("&lt;b&gt;Malo&lt;/b&gt;").doesNotContain("<b>Malo</b>");
		// Sin vencimiento, la línea no aparece.
		assertThat(message.path("Text").asString()).doesNotContain("Vencimiento");
	}

	@Test
	void aServerThatDoesNotAnswerIsAMailDeliveryException() {
		var unreachable = new JavaMailSenderImpl();
		unreachable.setHost("localhost");
		unreachable.setPort(1);
		var mailer = new SmtpMailer(unreachable, templates, "no-reply@norte.localhost");

		assertThatThrownBy(() -> mailer.send(invoiceMail(uniqueRecipient(), Locale.ROOT, "Globex", null)))
			.isInstanceOf(MailDeliveryException.class);
	}

	@Test
	void theSubjectIsTheTitleOfTheHtml() {
		assertThat(SmtpMailer.subjectOf("<html><head><title>\n  Factura &amp; recibo\n</title>", "x"))
			.isEqualTo("Factura & recibo");
		assertThatThrownBy(() -> SmtpMailer.subjectOf("<html></html>", "x")).isInstanceOf(IllegalStateException.class);
	}

	private static Mail invoiceMail(Email to, Locale locale, String customerName, String dueDate) {
		var variables = new HashMap<String, Object>(Map.of("customerName", customerName, "invoiceNumber",
				"F-2026-0001", "issueDate", "25 nov 2026", "total", "1.210,00 €"));
		if (dueDate != null) {
			variables.put("dueDate", dueDate);
		}
		return new Mail(to, "invoice", locale, variables, "Acme Ibérica S.L.", Optional.of(Email.of("facturas@acme.test")),
				List.of(new Mail.Attachment("F-2026-0001.pdf", "application/pdf", PDF)));
	}

	/** Mailpit guarda todos los correos de la ejecución: cada test usa su destinatario. */
	private static Email uniqueRecipient() {
		return Email.of("cliente-" + UUID.randomUUID() + "@norte.test");
	}

	private JsonNode received(Email to) throws Exception {
		String query = URLEncoder.encode("to:\"" + to.value() + "\"", StandardCharsets.UTF_8);
		JsonNode result = get("/search?query=" + query);
		assertThat(result.path("messages").size()).as("correos recibidos por %s", to).isEqualTo(1);
		return result.path("messages").get(0);
	}

	private JsonNode message(String id) throws Exception {
		return get("/message/" + id);
	}

	private JsonNode get(String path) throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(mailpit.apiUrl() + path)).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);
		return JSON.readTree(response.body());
	}

}
