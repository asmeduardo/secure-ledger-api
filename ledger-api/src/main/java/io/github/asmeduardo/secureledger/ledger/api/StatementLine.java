package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of a customer account with the transaction it belongs to.
 *
 * @param entryId position of the entry in the account history; only grows
 * @param amount signed: a credit is positive, a debit negative
 */
public record StatementLine(
        long entryId,
        UUID transactionId,
        TransactionKind kind,
        Money amount,
        Money balanceAfter,
        String reference,
        Instant postedAt) {}
