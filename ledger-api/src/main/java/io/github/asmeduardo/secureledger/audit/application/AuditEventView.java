package io.github.asmeduardo.secureledger.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
        long id,
        Instant occurredAt,
        String actor,
        String operation,
        String outcome,
        UUID resourceId,
        String idempotencyKey,
        String reason,
        Map<String, String> request) {}
