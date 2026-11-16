package io.github.asmeduardo.secureledger.audit.api;

public interface AuditTrail {

    /**
     * Appends an event. Joins the caller's transaction when there is one, so the event commits or
     * rolls back with the effect it describes; otherwise it is written in a transaction of its own.
     */
    void record(AuditRecord record);
}
