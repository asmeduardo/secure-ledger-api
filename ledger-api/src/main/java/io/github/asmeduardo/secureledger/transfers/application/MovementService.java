package io.github.asmeduardo.secureledger.transfers.application;

import io.github.asmeduardo.secureledger.accounts.api.OwnedAccounts;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.ledger.api.TransactionNotFoundException;
import io.github.asmeduardo.secureledger.shared.idempotency.Idempotency;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentRequest;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import io.github.asmeduardo.secureledger.shared.money.Money;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money movements requested by API clients. A client may only move money of its own accounts: an
 * account of another client is reported as not found, exactly like an unknown one. Every movement is
 * idempotent per client and key; the transaction is opened by {@link Idempotency}.
 */
@Service
public class MovementService {

    private final OwnedAccounts ownedAccounts;
    private final Ledger ledger;
    private final Idempotency idempotency;

    public MovementService(OwnedAccounts ownedAccounts, Ledger ledger, Idempotency idempotency) {
        this.ownedAccounts = ownedAccounts;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    public IdempotentResult<TransactionView> deposit(
            ClientId client, String key, UUID accountId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "DEPOSIT", accountId, amount, reference);
        return idempotency.execute(request, TransactionView.class, () -> {
            ownedAccounts.requireOwnedBy(client, accountId);
            return TransactionView.of(ledger.deposit(accountId, amount, client.value(), reference));
        });
    }

    /** A simulated payout: the money leaves the account towards the settlement account. */
    public IdempotentResult<TransactionView> withdraw(
            ClientId client, String key, UUID accountId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "WITHDRAWAL", accountId, amount, reference);
        return idempotency.execute(request, TransactionView.class, () -> {
            ownedAccounts.requireOwnedBy(client, accountId);
            return TransactionView.of(ledger.withdraw(accountId, amount, client.value(), reference));
        });
    }

    public IdempotentResult<TransactionView> transfer(
            ClientId client, String key, UUID sourceId, UUID destinationId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "TRANSFER", sourceId, destinationId, amount, reference);
        return idempotency.execute(request, TransactionView.class, () -> {
            ownedAccounts.requireOwnedBy(client, sourceId);
            ownedAccounts.requireOwnedBy(client, destinationId);
            return TransactionView.of(ledger.transfer(sourceId, destinationId, amount, client.value(), reference));
        });
    }

    /** A transaction is visible to the client that owns at least one of its accounts. */
    @Transactional(readOnly = true)
    public TransactionView get(ClientId client, UUID transactionId) {
        return ledger.find(transactionId)
                .filter(transaction -> !ownedAccounts
                        .ownedAmong(client, transaction.accountIds())
                        .isEmpty())
                .map(TransactionView::of)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }
}
