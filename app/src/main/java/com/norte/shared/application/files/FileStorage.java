package com.norte.shared.application.files;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Ficheros del tenant actual en un almacén compatible con S3 (paso 1.7). Cada ruta se guarda bajo
 * {@code {tenantId}/…}: el tenant sale siempre del contexto, nunca de quien llama.
 *
 * <p>Su primer uso es el PDF de la factura. Los ficheros no pasan por la API para descargarse: se da al navegador una
 * URL firmada de corta duración.
 */
public interface FileStorage {

	/** Validez por defecto de una URL firmada. */
	Duration DEFAULT_URL_VALIDITY = Duration.ofMinutes(5);

	/** Guarda o reemplaza el fichero. */
	void put(FilePath path, byte[] content, String contentType);

	Optional<byte[]> read(FilePath path);

	/**
	 * URL de descarga firmada, válida durante {@code validity}. No comprueba que el fichero exista.
	 * @param downloadName nombre con el que el navegador lo descarga
	 */
	URI signedUrl(FilePath path, Duration validity, String downloadName);

	/** Borra el fichero; no es un error que no exista. */
	void delete(FilePath path);

}
