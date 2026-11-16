package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.accounts.api.OwnedAccounts;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.shared.pagination.Cursor;
import io.github.asmeduardo.secureledger.shared.pagination.CursorPage;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statements of the client's own accounts. */
@Service
public class StatementService {

    /** Longest period one export may cover. */
    static final Duration MAX_EXPORT_PERIOD = Duration.ofDays(366);

    private static final int EXPORT_PAGE_SIZE = 500;

    private final OwnedAccounts ownedAccounts;
    private final Ledger ledger;

    public StatementService(OwnedAccounts ownedAccounts, Ledger ledger) {
        this.ownedAccounts = ownedAccounts;
        this.ledger = ledger;
    }

    /** Newest entries first; the cursor continues strictly below the last entry already returned. */
    @Transactional(readOnly = true)
    public CursorPage<StatementLineView> page(ClientId client, UUID accountId, String cursor, int limit) {
        ownedAccounts.requireOwnedBy(client, accountId);
        var rows = ledger.statement(accountId, Cursor.decode(cursor), CursorPage.rowsToRead(limit)).stream()
                .map(StatementLineView::of)
                .toList();
        return CursorPage.of(rows, limit, StatementLineView::entryId);
    }

    /**
     * Checks the request and fixes the entries the export will contain: those of the period posted
     * up to now. Nothing is read until the export is consumed, so validation errors come first.
     *
     * @param from first instant included
     * @param to first instant excluded
     */
    public StatementExport export(ClientId client, UUID accountId, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidPeriodException("The period must end after it starts");
        }
        if (Duration.between(from, to).compareTo(MAX_EXPORT_PERIOD) > 0) {
            throw new InvalidPeriodException("A statement export covers at most 366 days");
        }
        ownedAccounts.requireOwnedBy(client, accountId);
        return new StatementExport(ledger, accountId, from, to, ledger.latestEntryId(accountId), EXPORT_PAGE_SIZE);
    }
}
