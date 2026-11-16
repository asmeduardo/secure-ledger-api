package io.github.asmeduardo.secureledger.ledger.api;

import java.util.UUID;

public class AlreadyReversedException extends RuntimeException {

    public AlreadyReversedException(UUID transactionId) {
        super("Transaction " + transactionId + " has already been reversed");
    }
}
