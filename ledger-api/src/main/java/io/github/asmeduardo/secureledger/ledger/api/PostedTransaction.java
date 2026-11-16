package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An immutable view of a posted transaction. Because posted transactions never change, the same
 * transaction always produces the same view.
 *
 * @param amount the money moved: the sum of the credits, which equals the sum of the debits
 */
public record PostedTransaction(
        UUID id,
        TransactionKind kind,
        Money amount,
        String initiatedBy,
        Optional<String> reference,
        Optional<UUID> reversesId,
        Instant createdAt,
        List<PostedEntry> entries) {

    public PostedTransaction {
        entries = List.copyOf(entries);
    }

    /** Customer and settlement accounts touched by this transaction. */
    public List<UUID> accountIds() {
        return entries.stream().map(PostedEntry::accountId).toList();
    }
}
