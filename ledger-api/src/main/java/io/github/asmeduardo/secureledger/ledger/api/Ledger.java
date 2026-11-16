package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the rest of the application may ask of the ledger. Every method joins the caller's
 * transaction when there is one, so a movement and whatever the caller records with it (an
 * idempotency key, an audit event) are committed or rolled back together.
 */
public interface Ledger {

    void openCustomerAccount(UUID accountId, Currency currency);

    /** Current balance of each customer account; unknown ids are left out. */
    Map<UUID, Money> balances(Collection<UUID> accountIds);

    /** Credits the account; the settlement account of the currency is the counterpart. */
    PostedTransaction deposit(UUID accountId, Money amount, String initiatedBy, String reference);

    /** Debits the account (a simulated payout); the settlement account is the counterpart. */
    PostedTransaction withdraw(UUID accountId, Money amount, String initiatedBy, String reference);

    PostedTransaction transfer(UUID sourceId, UUID destinationId, Money amount, String initiatedBy, String reference);

    /** Posts the opposite entries of a transaction; a transaction is reversed at most once. */
    PostedTransaction reverse(UUID transactionId, String initiatedBy, String reference);

    Optional<PostedTransaction> find(UUID transactionId);

    /**
     * Entries of a customer account posted strictly before the given entry id, newest first. Because
     * entries are append-only and their ids only grow per account, a page never changes once read.
     */
    List<StatementLine> statement(UUID accountId, long beforeEntryId, int limit);

    /**
     * Entries of a customer account posted in {@code [from, to)}, oldest first, after {@code afterEntryId}
     * and up to {@code upToEntryId} included. Bounding the ids keeps a long export consistent.
     */
    List<StatementLine> statementBetween(
            UUID accountId, Instant from, Instant to, long afterEntryId, long upToEntryId, int limit);

    /** Id of the newest entry of the account, or zero when it has none. */
    long latestEntryId(UUID accountId);
}
