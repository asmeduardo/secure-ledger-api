package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.ledger.api.StatementLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A statement line as the account owner sees it: absolute amount with a direction, and the balance
 * of the account right after the entry.
 */
public record StatementLineView(
        long entryId,
        UUID transactionId,
        String type,
        String direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String reference,
        Instant postedAt) {

    static StatementLineView of(StatementLine line) {
        return new StatementLineView(
                line.entryId(),
                line.transactionId(),
                line.kind().name(),
                line.amount().isPositive() ? "CREDIT" : "DEBIT",
                line.amount().amount().abs(),
                line.amount().currency().getCurrencyCode(),
                line.balanceAfter().amount(),
                line.reference(),
                line.postedAt());
    }
}
