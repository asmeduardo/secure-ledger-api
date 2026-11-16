package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * One side of a transaction before it is posted: a signed amount for one account. A positive amount
 * is a credit and raises the balance of the account; a negative amount is a debit.
 */
public record Leg(UUID accountId, Money amount) {

    public Leg {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
    }

    public static Leg credit(UUID accountId, Money amount) {
        return new Leg(accountId, amount);
    }

    public static Leg debit(UUID accountId, Money amount) {
        return new Leg(accountId, amount.negate());
    }
}
