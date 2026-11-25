package com.norte.shared.infrastructure.files;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Almacén de ficheros compatible con S3 (paso 1.7): SeaweedFS en local y en el servidor.
 *
 * @param endpoint dirección con la que la aplicación habla con el almacén (en producción, por la red interna)
 * @param publicEndpoint dirección con la que el navegador abre las URLs firmadas; sin ella, la misma que
 * {@code endpoint}. La firma incluye el host, así que tiene que ser la que de verdad usa el navegador.
 */
@ConfigurationProperties("norte.files")
record FilesProperties(String endpoint, String publicEndpoint, @DefaultValue("us-east-1") String region,
		@DefaultValue("norte") String bucket, String accessKey, String secretKey) {

	String browserEndpoint() {
		return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
	}

}
