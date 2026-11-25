package com.norte.shared.application.mail;

/**
 * Envío de correo con plantillas (paso 1.8). Su primer uso es mandar la factura en PDF.
 *
 * <p>Envía en el momento y espera a que el servidor SMTP acepte el mensaje. Los reintentos y el envío en segundo
 * plano llegan con JobRunr en el hito 2.
 */
public interface Mailer {

	/** @throws MailDeliveryException si el servidor de correo no lo acepta */
	void send(Mail mail);

}
