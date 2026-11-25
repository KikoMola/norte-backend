package com.norte.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Servidor SMTP de pruebas (paso 1.8): recoge los correos y los enseña por su API HTTP. */
public class MailpitContainer extends GenericContainer<MailpitContainer> {

	public static final String IMAGE = "axllent/mailpit:v1.34.0";

	private static final int SMTP_PORT = 1025;

	private static final int HTTP_PORT = 8025;

	public MailpitContainer() {
		super(DockerImageName.parse(IMAGE));
		withExposedPorts(SMTP_PORT, HTTP_PORT);
		waitingFor(Wait.forHttp("/api/v1/info").forPort(HTTP_PORT).forStatusCode(200));
	}

	public int smtpPort() {
		return getMappedPort(SMTP_PORT);
	}

	/** Base de la API de Mailpit: {@code /api/v1/messages}, {@code /api/v1/search}… */
	public String apiUrl() {
		return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT) + "/api/v1";
	}

}
