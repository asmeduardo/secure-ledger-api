package io.github.asmeduardo.secureledger.transfers.application;

import io.github.asmeduardo.secureledger.accounts.api.OwnedAccounts;
import io.github.asmeduardo.secureledger.audit.api.AuditedWrites;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.ledger.api.PostedEntry;
import io.github.asmeduardo.secureledger.ledger.api.TransactionNotFoundException;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentRequest;
import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import io.github.asmeduardo.secureledger.shared.money.Money;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money movements requested by API clients. A client may only move money of its own accounts: an
 * account of another client is reported as not found, exactly like an unknown one. Every movement is
 * idempotent per client and key and leaves an audit event; see {@link AuditedWrites}.
 */
@Service
public class MovementService {

    private final OwnedAccounts ownedAccounts;
    private final Ledger ledger;
    private final AuditedWrites writes;

    public MovementService(OwnedAccounts ownedAccounts, Ledger ledger, AuditedWrites writes) {
        this.ownedAccounts = ownedAccounts;
        this.ledger = ledger;
        this.writes = writes;
    }

    public IdempotentResult<TransactionView> deposit(
            ClientId client, String key, UUID accountId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "DEPOSIT", accountId, amount, reference);
        return writes.execute(request, TransactionView.class, details(accountId, amount), TransactionView::id, () -> {
            ownedAccounts.requireOwnedBy(client, accountId);
            return TransactionView.of(ledger.deposit(accountId, amount, client.value(), reference));
        });
    }

    /** A simulated payout: the money leaves the account towards the settlement account. */
    public IdempotentResult<TransactionView> withdraw(
            ClientId client, String key, UUID accountId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "WITHDRAWAL", accountId, amount, reference);
        return writes.execute(request, TransactionView.class, details(accountId, amount), TransactionView::id, () -> {
            ownedAccounts.requireOwnedBy(client, accountId);
            return TransactionView.of(ledger.withdraw(accountId, amount, client.value(), reference));
        });
    }

    public IdempotentResult<TransactionView> transfer(
            ClientId client, String key, UUID sourceId, UUID destinationId, Money amount, String reference) {
        var request = IdempotentRequest.of(client, key, "TRANSFER", sourceId, destinationId, amount, reference);
        var details = Map.of(
                "sourceAccountId", sourceId.toString(),
                "destinationAccountId", destinationId.toString(),
                "amount", amount.amount().toPlainString(),
                "currency", amount.currency().getCurrencyCode());
        return writes.execute(request, TransactionView.class, details, TransactionView::id, () -> {
            ownedAccounts.requireOwnedBy(client, sourceId);
            ownedAccounts.requireOwnedBy(client, destinationId);
            return TransactionView.of(ledger.transfer(sourceId, destinationId, amount, client.value(), reference));
        });
    }

    /**
     * Corrects a transaction by posting its opposite entries. Only the client that owns every customer
     * account of the transaction may reverse it; anyone else is told the transaction does not exist.
     */
    public IdempotentResult<TransactionView> reverse(
            ClientId client, String key, UUID transactionId, String reference) {
        var request = IdempotentRequest.of(client, key, "REVERSAL", transactionId, reference);
        var details = Map.of("transactionId", transactionId.toString());
        return writes.execute(request, TransactionView.class, details, TransactionView::id, () -> {
            var original =
                    ledger.find(transactionId).orElseThrow(() -> new TransactionNotFoundException(transactionId));
            var customerAccounts = original.entries().stream()
                    .filter(entry -> entry.balanceAfter().isPresent())
                    .map(PostedEntry::accountId)
                    .toList();
            if (!ownedAccounts.ownedAmong(client, customerAccounts).containsAll(customerAccounts)) {
                throw new TransactionNotFoundException(transactionId);
            }
            return TransactionView.of(ledger.reverse(transactionId, client.value(), reference));
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

    private static Map<String, String> details(UUID accountId, Money amount) {
        return Map.of(
                "accountId", accountId.toString(),
                "amount", amount.amount().toPlainString(),
                "currency", amount.currency().getCurrencyCode());
    }
}
