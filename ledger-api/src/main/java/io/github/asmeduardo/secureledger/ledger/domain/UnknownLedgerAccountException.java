package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.UUID;

public class UnknownLedgerAccountException extends RuntimeException {

    public UnknownLedgerAccountException(UUID accountId) {
        super("Ledger account " + accountId + " does not exist");
    }
}
