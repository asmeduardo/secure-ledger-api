package io.github.asmeduardo.secureledger.audit.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @param resourceId account or transaction created or replayed; {@code null} when rejected
 * @param reason problem code of the refusing rule; only for {@link AuditOutcome#REJECTED}
 * @param request identifiers and amounts of the request, never free text
 */
public record AuditRecord(
        String actor,
        String operation,
        AuditOutcome outcome,
        UUID resourceId,
        String idempotencyKey,
        String reason,
        Map<String, String> request) {

    public AuditRecord {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(outcome, "outcome");
        request = Map.copyOf(request);
    }
}
