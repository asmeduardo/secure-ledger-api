package io.github.asmeduardo.secureledger.shared.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistence of the idempotency records. Runs inside the caller's transaction. */
@Repository
class IdempotencyRecords {

    record Stored(String fingerprint, String responseBody) {}

    private final JdbcTemplate jdbc;

    IdempotencyRecords(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Stored> find(IdempotentRequest request) {
        return jdbc
                .query(
                        """
                        select request_fingerprint, response_body::text from idempotency.idempotency_record
                         where client_id = ? and idempotency_key = ?
                        """,
                        (row, index) -> new Stored(row.getString(1), row.getString(2)),
                        request.client().value(),
                        request.key())
                .stream()
                .findFirst();
    }

    /**
     * Claims the key. A concurrent transaction that claimed it first makes this insert wait for its
     * outcome and then fail with a duplicate key.
     */
    void claim(IdempotentRequest request, Instant now) {
        jdbc.update(
                """
                insert into idempotency.idempotency_record
                    (client_id, idempotency_key, operation, request_fingerprint, created_at)
                values (?, ?, ?, ?, ?)
                """,
                request.client().value(),
                request.key(),
                request.operation(),
                request.fingerprint(),
                Timestamp.from(now));
    }

    void complete(IdempotentRequest request, String responseBody) {
        jdbc.update("""
                update idempotency.idempotency_record set response_body = ?::jsonb
                 where client_id = ? and idempotency_key = ?
                """, responseBody, request.client().value(), request.key());
    }
}
