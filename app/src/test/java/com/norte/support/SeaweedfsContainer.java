package com.norte.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Almacén de ficheros S3 (paso 1.7), con las mismas credenciales que en local ({@code infra/seaweedfs/s3.json}). */
public class SeaweedfsContainer extends GenericContainer<SeaweedfsContainer> {

	public static final String IMAGE = "chrislusf/seaweedfs:4.55";

	private static final int S3_PORT = 8333;

	public SeaweedfsContainer() {
		super(DockerImageName.parse(IMAGE));
		withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json");
		withCopyFileToContainer(MountableFile.forHostPath("../infra/seaweedfs/s3.json"), "/etc/seaweedfs/s3.json");
		withExposedPorts(S3_PORT);
		// Sin credenciales responde 403: la API S3 ya está atendiendo.
		waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(403));
	}

	public String s3Endpoint() {
		return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
	}

}
