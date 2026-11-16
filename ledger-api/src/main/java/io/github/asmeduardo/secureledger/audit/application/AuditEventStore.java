package io.github.asmeduardo.secureledger.audit.application;

import io.github.asmeduardo.secureledger.audit.api.AuditRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Append-only storage of audit events; the table rejects updates and deletes. */
@Repository
class AuditEventStore {

    private static final TypeReference<Map<String, String>> REQUEST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    AuditEventStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void append(AuditRecord record, Instant now) {
        jdbc.update(
                """
                insert into audit.audit_event
                    (occurred_at, actor, operation, outcome, resource_id, idempotency_key, reason, request)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                Timestamp.from(now),
                record.actor(),
                record.operation(),
                record.outcome().name(),
                record.resourceId(),
                record.idempotencyKey(),
                record.reason(),
                json.writeValueAsString(record.request()));
    }

    /** Newest first, strictly below {@code beforeId}; {@code actor} null means every actor. */
    List<AuditEventView> page(String actor, long beforeId, int rows) {
        return jdbc.query("""
                select id, occurred_at, actor, operation, outcome, resource_id, idempotency_key, reason,
                       request::text as request
                  from audit.audit_event
                 where id < ? and (cast(? as varchar) is null or actor = ?)
                 order by id desc
                 limit ?
                """, this::view, beforeId, actor, actor, rows);
    }

    private AuditEventView view(ResultSet row, int index) throws SQLException {
        return new AuditEventView(
                row.getLong("id"),
                row.getTimestamp("occurred_at").toInstant(),
                row.getString("actor"),
                row.getString("operation"),
                row.getString("outcome"),
                row.getObject("resource_id", UUID.class),
                row.getString("idempotency_key"),
                row.getString("reason"),
                json.readValue(row.getString("request"), REQUEST));
    }
}
