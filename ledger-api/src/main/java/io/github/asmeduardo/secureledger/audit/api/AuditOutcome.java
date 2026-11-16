package io.github.asmeduardo.secureledger.audit.api;

public enum AuditOutcome {
    /** The request took effect. Recorded in the same transaction as the effect. */
    SUCCEEDED,
    /** A retry answered with the stored response of an earlier request; nothing changed. */
    REPLAYED,
    /** A business rule refused the request; nothing changed. */
    REJECTED
}
