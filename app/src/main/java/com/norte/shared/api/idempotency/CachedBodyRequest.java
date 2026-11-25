package com.norte.shared.api.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.util.StreamUtils;

/**
 * Petición con el cuerpo ya leído, para calcular su huella y que el controlador lo pueda volver a leer. Los cuerpos
 * de la API son JSON pequeños; las subidas de ficheros no usan Idempotency-Key.
 */
final class CachedBodyRequest extends HttpServletRequestWrapper {

	private final byte[] body;

	CachedBodyRequest(HttpServletRequest request) throws IOException {
		super(request);
		this.body = StreamUtils.copyToByteArray(request.getInputStream());
	}

	byte[] body() {
		return body;
	}

	@Override
	public ServletInputStream getInputStream() {
		var input = new ByteArrayInputStream(body);
		return new ServletInputStream() {

			@Override
			public boolean isFinished() {
				return input.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener listener) {
				throw new UnsupportedOperationException("Synchronous reads only");
			}

			@Override
			public int read() {
				return input.read();
			}

			@Override
			public int read(byte[] buffer, int offset, int length) {
				return input.read(buffer, offset, length);
			}

		};
	}

	@Override
	public BufferedReader getReader() {
		Charset charset = getCharacterEncoding() == null ? StandardCharsets.UTF_8
				: Charset.forName(getCharacterEncoding());
		return new BufferedReader(new InputStreamReader(getInputStream(), charset));
	}

}
