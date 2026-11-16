package io.github.asmeduardo.secureledger.transfers.application;

import io.github.asmeduardo.secureledger.ledger.api.PostedTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A posted transaction as returned by the API. Entry amounts are absolute values with a direction:
 * a credit raises the balance of the account and a debit lowers it.
 */
public record TransactionView(
        UUID id,
        String type,
        BigDecimal amount,
        String currency,
        String reference,
        UUID reversesTransactionId,
        Instant createdAt,
        List<EntryView> entries) {

    public record EntryView(UUID accountId, Direction direction, BigDecimal amount, BigDecimal balanceAfter) {}

    public enum Direction {
        CREDIT,
        DEBIT
    }

    static TransactionView of(PostedTransaction transaction) {
        var entries = transaction.entries().stream()
                .map(entry -> new EntryView(
                        entry.accountId(),
                        entry.amount().isPositive() ? Direction.CREDIT : Direction.DEBIT,
                        entry.amount().amount().abs(),
                        entry.balanceAfter().map(balance -> balance.amount()).orElse(null)))
                .toList();
        return new TransactionView(
                transaction.id(),
                transaction.kind().name(),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.reference().orElse(null),
                transaction.reversesId().orElse(null),
                transaction.createdAt(),
                entries);
    }
}
