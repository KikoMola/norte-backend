package com.norte.shared.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.norte.shared.application.IdempotencyStore;
import com.norte.shared.application.TenantContext;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

	static final Duration RETENTION = Duration.ofHours(24);

	/** Una petición en curso que no termina en este tiempo se da por abandonada (el proceso murió a medias). */
	static final Duration ABANDONED_AFTER = Duration.ofMinutes(1);

	private final JdbcClient jdbc;

	private final TransactionTemplate transactions;

	private final Clock clock;

	JdbcIdempotencyStore(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Override
	public Claim claim(UUID key, String requestHash) {
		UUID tenantId = TenantContext.requireTenantId();
		return transactions.execute(status -> {
			Instant now = clock.instant();
			int inserted = jdbc.sql("""
					insert into shared.idempotency_key (tenant_id, key, request_hash, created_at)
					values (?, ?, ?, ?) on conflict do nothing""")
				.params(tenantId, key, requestHash, Timestamp.from(now))
				.update();
			if (inserted == 1) {
				return new Claim.Acquired();
			}
			Row row = jdbc.sql("""
					select request_hash, status, content_type, location, etag, body, created_at, completed_at
					from shared.idempotency_key where tenant_id = ? and key = ? for update""")
				.params(tenantId, key)
				.query((rs, n) -> new Row(rs.getString("request_hash"), rs.getInt("status"),
						rs.getString("content_type"), rs.getString("location"), rs.getString("etag"),
						rs.getBytes("body"), rs.getTimestamp("created_at").toInstant(),
						Optional.ofNullable(rs.getTimestamp("completed_at")).map(Timestamp::toInstant)))
				.single();
			boolean expired = row.createdAt().isBefore(now.minus(RETENTION));
			boolean abandoned = row.completedAt().isEmpty() && row.createdAt().isBefore(now.minus(ABANDONED_AFTER));
			if (expired || abandoned) {
				jdbc.sql("""
						update shared.idempotency_key
						set request_hash = ?, status = null, content_type = null, location = null, etag = null,
						    body = null, created_at = ?, completed_at = null
						where tenant_id = ? and key = ?""")
					.params(requestHash, Timestamp.from(now), tenantId, key)
					.update();
				return new Claim.Acquired();
			}
			if (!row.requestHash().equals(requestHash)) {
				return new Claim.Mismatch();
			}
			if (row.completedAt().isEmpty()) {
				return new Claim.InProgress();
			}
			return new Claim.Replay(
					new StoredResponse(row.status(), row.contentType(), row.location(), row.etag(), row.body()));
		});
	}

	@Override
	public void complete(UUID key, StoredResponse response) {
		UUID tenantId = TenantContext.requireTenantId();
		transactions.executeWithoutResult(status -> jdbc.sql("""
				update shared.idempotency_key
				set status = ?, content_type = ?, location = ?, etag = ?, body = ?, completed_at = ?
				where tenant_id = ? and key = ?""")
			.params(response.status(), response.contentType(), response.location(), response.etag(),
					response.body(), Timestamp.from(clock.instant()), tenantId, key)
			.update());
	}

	@Override
	public void release(UUID key) {
		UUID tenantId = TenantContext.requireTenantId();
		transactions.executeWithoutResult(status -> jdbc
			.sql("delete from shared.idempotency_key where tenant_id = ? and key = ? and completed_at is null")
			.params(tenantId, key)
			.update());
	}

	private record Row(String requestHash, int status, String contentType, String location, String etag, byte[] body,
			Instant createdAt, Optional<Instant> completedAt) {
	}

}
