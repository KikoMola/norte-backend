package com.norte.shared.api.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import com.norte.shared.api.problem.Problems;
import com.norte.shared.application.IdempotencyStore;
import com.norte.shared.application.IdempotencyStore.Claim;
import com.norte.shared.application.IdempotencyStore.StoredResponse;
import com.norte.shared.application.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * {@code Idempotency-Key} en los POST (paso 1.6). La primera petición con una clave se ejecuta y su respuesta se guarda;
 * las siguientes con la misma clave reciben esa respuesta (con {@code Idempotency-Replayed: true}) sin repetir el
 * efecto. Así un reintento tras un corte de red no emite dos facturas.
 *
 * <ul>
 * <li>Misma clave con otra petición (otra ruta u otro cuerpo): 422 {@code idempotency.key_reused}.
 * <li>Misma clave mientras la primera aún se ejecuta: 409 {@code idempotency.in_progress}.
 * <li>Las respuestas 5xx no se guardan: el cliente puede reintentar con la misma clave.
 * </ul>
 *
 * Corre en la cadena de seguridad después del filtro del tenant: las claves son por tenant y viven en
 * {@code shared.idempotency_key} con RLS.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

	public static final String HEADER = "Idempotency-Key";

	public static final String REPLAYED_HEADER = "Idempotency-Replayed";

	private final IdempotencyStore store;

	private final Problems problems;

	private final JsonMapper json;

	IdempotencyFilter(IdempotencyStore store, Problems problems, JsonMapper json) {
		this.store = store;
		this.problems = problems;
		this.json = json;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod()) || request.getHeader(HEADER) == null
				|| TenantContext.currentTenantId().isEmpty();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		UUID key;
		try {
			key = UUID.fromString(request.getHeader(HEADER));
		}
		catch (IllegalArgumentException ex) {
			writeProblem(response, HttpStatus.BAD_REQUEST, "request.invalid_parameter",
					"Parámetros de la petición no válidos", "Idempotency-Key tiene que ser un UUID.");
			return;
		}
		var cached = new CachedBodyRequest(request);
		String hash = hash(request.getMethod(), request.getRequestURI(), cached.body());

		Claim claim = store.claim(key, hash);
		switch (claim) {
			case Claim.Replay(StoredResponse stored) -> replay(response, stored);
			case Claim.Mismatch() -> writeProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "idempotency.key_reused",
					"La clave de idempotencia ya se usó con otra petición",
					"Genera una clave nueva para cada operación distinta.");
			case Claim.InProgress() -> writeProblem(response, HttpStatus.CONFLICT, "idempotency.in_progress",
					"La petición original todavía se está procesando", "Espera unos segundos y vuelve a intentarlo.");
			case Claim.Acquired() -> execute(cached, response, chain, key);
		}
	}

	private void execute(CachedBodyRequest request, HttpServletResponse response, FilterChain chain, UUID key)
			throws ServletException, IOException {
		var wrapper = new ContentCachingResponseWrapper(response);
		boolean stored = false;
		try {
			chain.doFilter(request, wrapper);
			if (wrapper.getStatus() < 500) {
				store.complete(key, new StoredResponse(wrapper.getStatus(), wrapper.getContentType(),
						wrapper.getHeader(HttpHeaders.LOCATION), wrapper.getHeader(HttpHeaders.ETAG),
						wrapper.getContentAsByteArray()));
				stored = true;
			}
		}
		finally {
			if (!stored) {
				store.release(key);
			}
			wrapper.copyBodyToResponse();
		}
	}

	private static void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
		response.setStatus(stored.status());
		response.setHeader(REPLAYED_HEADER, "true");
		if (stored.contentType() != null) {
			response.setContentType(stored.contentType());
		}
		if (stored.location() != null) {
			response.setHeader(HttpHeaders.LOCATION, stored.location());
		}
		if (stored.etag() != null) {
			response.setHeader(HttpHeaders.ETAG, stored.etag());
		}
		if (stored.body() != null) {
			StreamUtils.copy(stored.body(), response.getOutputStream());
		}
	}

	private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String title,
			String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(Problems.PROBLEM_JSON.toString());
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), problems.problem(status, code, title, detail));
	}

	static String hash(String method, String path, byte[] body) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			digest.update(method.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) ' ');
			digest.update(path.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) '\n');
			digest.update(body);
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
