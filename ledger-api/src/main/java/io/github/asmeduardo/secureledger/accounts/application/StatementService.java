package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.accounts.api.OwnedAccounts;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.shared.pagination.Cursor;
import io.github.asmeduardo.secureledger.shared.pagination.CursorPage;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Statements of the client's own accounts. */
@Service
public class StatementService {

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
        var rows = ledger.statement(accountId, Cursor.decode(cursor), limit + 1).stream()
                .map(StatementLineView::of)
                .toList();
        return CursorPage.of(rows, limit, StatementLineView::entryId);
    }
}
