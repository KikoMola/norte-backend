package com.norte.shared.infrastructure.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.application.TenantContext;
import com.norte.shared.application.files.FilePath;
import com.norte.shared.application.files.FileStorage;
import com.norte.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

/** Ficheros en SeaweedFS real (paso 1.7): espacio por tenant y URLs firmadas. */
class FileStorageTests extends IntegrationTest {

	private static final byte[] PDF = "%PDF-1.7 factura de prueba".getBytes(StandardCharsets.UTF_8);

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	FileStorage files;

	@Test
	void storesAndReadsAFileOfTheTenant() {
		FilePath path = uniquePath();
		TenantContext.runAs(TENANT_A, () -> files.put(path, PDF, "application/pdf"));

		assertThat(read(TENANT_A, path)).hasValue(PDF);
	}

	@Test
	void theSamePathOfAnotherTenantIsAnotherFile() {
		FilePath path = uniquePath();
		TenantContext.runAs(TENANT_A, () -> files.put(path, PDF, "application/pdf"));

		assertThat(read(TENANT_B, path)).isEmpty();
	}

	@Test
	void signedUrlDownloadsTheFileWithoutCredentials() throws Exception {
		FilePath path = uniquePath();
		URI url = TenantContext.callAs(TENANT_A, () -> {
			files.put(path, PDF, "application/pdf");
			return files.signedUrl(path, Duration.ofMinutes(1), "Factura F-2026-0001.pdf");
		});

		HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(url).build(),
				HttpResponse.BodyHandlers.ofByteArray());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo(PDF);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
		assertThat(response.headers().firstValue("Content-Disposition"))
			.hasValue("attachment; filename*=UTF-8''Factura%20F-2026-0001.pdf");
	}

	@Test
	void aTamperedSignedUrlIsRejected() throws Exception {
		FilePath path = uniquePath();
		URI url = TenantContext.callAs(TENANT_A, () -> {
			files.put(path, PDF, "application/pdf");
			return files.signedUrl(path, Duration.ofMinutes(1), "factura.pdf");
		});
		// Misma firma, pero apuntando a la carpeta de otro tenant.
		URI tampered = URI.create(url.toString().replace(TENANT_A.toString(), TENANT_B.toString()));

		assertThat(status(tampered)).isEqualTo(403);
	}

	@Test
	void unsignedRequestsAreRejected() throws Exception {
		FilePath path = uniquePath();
		URI url = TenantContext.callAs(TENANT_A, () -> {
			files.put(path, PDF, "application/pdf");
			return files.signedUrl(path, Duration.ofMinutes(1), "factura.pdf");
		});
		URI unsigned = URI.create(url.toString().substring(0, url.toString().indexOf('?')));

		assertThat(status(unsigned)).isEqualTo(403);
	}

	@Test
	void deletingRemovesTheFileAndIsIdempotent() {
		FilePath path = uniquePath();
		TenantContext.runAs(TENANT_A, () -> {
			files.put(path, PDF, "application/pdf");
			files.delete(path);
			files.delete(path);
		});

		assertThat(read(TENANT_A, path)).isEmpty();
	}

	private Optional<byte[]> read(UUID tenantId, FilePath path) {
		try {
			return TenantContext.callAs(tenantId, () -> files.read(path));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private int status(URI url) throws Exception {
		return http.send(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	/** El almacén no se vacía entre tests: cada uno usa su propia ruta. */
	private static FilePath uniquePath() {
		return FilePath.of("tests", UUID.randomUUID() + ".pdf");
	}

}
