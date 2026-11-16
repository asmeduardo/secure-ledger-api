package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A validated export of one account and period. It reads the entries in short pages, each in its own
 * read-only transaction, so a long export holds no transaction open while the client downloads it.
 * Entries are append-only and capped at the newest id seen when the export was created, so the
 * pages together form one consistent statement.
 */
public final class StatementExport {

    private final Ledger ledger;
    private final UUID accountId;
    private final Instant from;
    private final Instant to;
    private final long upToEntryId;
    private final int pageSize;

    StatementExport(Ledger ledger, UUID accountId, Instant from, Instant to, long upToEntryId, int pageSize) {
        this.ledger = ledger;
        this.accountId = accountId;
        this.from = from;
        this.to = to;
        this.upToEntryId = upToEntryId;
        this.pageSize = pageSize;
    }

    public UUID accountId() {
        return accountId;
    }

    public Instant from() {
        return from;
    }

    public Instant to() {
        return to;
    }

    /** Hands every line of the period to the sink, oldest first. */
    public void forEach(Consumer<StatementLineView> sink) {
        long after = 0;
        while (true) {
            var page = ledger.statementBetween(accountId, from, to, after, upToEntryId, pageSize);
            page.forEach(line -> sink.accept(StatementLineView.of(line)));
            if (page.size() < pageSize) {
                return;
            }
            after = page.getLast().entryId();
        }
    }
}
