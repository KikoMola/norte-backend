package com.norte.shared.infrastructure.files;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.norte.shared.application.TenantContext;
import com.norte.shared.application.files.FilePath;
import com.norte.shared.application.files.FileStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * {@link FileStorage} sobre la API de S3 con el SDK de AWS. Sirve igual para SeaweedFS que para S3 o cualquier
 * almacén compatible: solo cambia la configuración.
 */
@Component
@EnableConfigurationProperties(FilesProperties.class)
class S3FileStorage implements FileStorage, DisposableBean {

	private final FilesProperties properties;

	private final S3Client client;

	private final S3Presigner presigner;

	S3FileStorage(FilesProperties properties) {
		this.properties = properties;
		var credentials = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		// Rutas «endpoint/bucket/clave»: los almacenes propios no tienen un subdominio por bucket.
		var s3 = S3Configuration.builder().pathStyleAccessEnabled(true).build();
		this.client = S3Client.builder()
			.endpointOverride(URI.create(properties.endpoint()))
			.region(Region.of(properties.region()))
			.credentialsProvider(credentials)
			.serviceConfiguration(s3)
			// Las sumas de comprobación nuevas del SDK solo cuando la operación las exige: no todos los almacenes
			// compatibles las entienden.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build();
		this.presigner = S3Presigner.builder()
			.endpointOverride(URI.create(properties.browserEndpoint()))
			.region(Region.of(properties.region()))
			.credentialsProvider(credentials)
			.serviceConfiguration(s3)
			.build();
	}

	@Override
	public void put(FilePath path, byte[] content, String contentType) {
		String key = key(path);
		try {
			upload(key, content, contentType);
		}
		catch (NoSuchBucketException ex) {
			// Primera escritura en un almacén vacío: se crea el bucket y se reintenta. Así la aplicación arranca
			// aunque el almacén aún no esté listo.
			createBucket();
			upload(key, content, contentType);
		}
	}

	@Override
	public Optional<byte[]> read(FilePath path) {
		try {
			return Optional
				.of(client.getObjectAsBytes(request -> request.bucket(properties.bucket()).key(key(path))).asByteArray());
		}
		catch (NoSuchKeyException | NoSuchBucketException ex) {
			return Optional.empty();
		}
	}

	@Override
	public URI signedUrl(FilePath path, Duration validity, String downloadName) {
		String disposition = "attachment; filename*=UTF-8''"
				+ UriUtils.encode(downloadName, StandardCharsets.UTF_8);
		var signed = presigner.presignGetObject(request -> request.signatureDuration(validity)
			.getObjectRequest(get -> get.bucket(properties.bucket())
				.key(key(path))
				.responseContentDisposition(disposition)));
		return URI.create(signed.url().toString());
	}

	@Override
	public void delete(FilePath path) {
		try {
			client.deleteObject(request -> request.bucket(properties.bucket()).key(key(path)));
		}
		catch (NoSuchBucketException ex) {
			// Nada que borrar.
		}
	}

	@Override
	public void destroy() {
		presigner.close();
		client.close();
	}

	private void upload(String key, byte[] content, String contentType) {
		client.putObject(request -> request.bucket(properties.bucket()).key(key).contentType(contentType),
				RequestBody.fromBytes(content));
	}

	private void createBucket() {
		try {
			client.createBucket(request -> request.bucket(properties.bucket()));
		}
		catch (BucketAlreadyOwnedByYouException ex) {
			// Otra instancia lo creó a la vez.
		}
	}

	/** Clave completa: el tenant del contexto delante de la ruta. */
	private static String key(FilePath path) {
		return TenantContext.requireTenantId() + "/" + path.value();
	}

}
