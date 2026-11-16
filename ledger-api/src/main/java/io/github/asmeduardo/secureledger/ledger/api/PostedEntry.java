package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.util.Optional;
import java.util.UUID;

/**
 * @param amount signed: a credit is positive, a debit negative
 * @param balanceAfter balance of a customer account right after the entry; empty for settlement
 */
public record PostedEntry(UUID accountId, Money amount, Optional<Money> balanceAfter) {}
