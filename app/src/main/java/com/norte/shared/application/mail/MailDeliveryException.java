package com.norte.shared.application.mail;

/** El servidor de correo no aceptó el mensaje: caído, credenciales malas o destinatario rechazado. */
public class MailDeliveryException extends RuntimeException {

	public MailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}

}
