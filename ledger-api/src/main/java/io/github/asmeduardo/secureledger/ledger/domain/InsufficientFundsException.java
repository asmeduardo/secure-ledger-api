package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.UUID;

/** A posting would leave a customer account with a negative balance. */
public class InsufficientFundsException extends RuntimeException {

    private final UUID accountId;

    public InsufficientFundsException(UUID accountId) {
        super("Account " + accountId + " does not have enough funds");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
