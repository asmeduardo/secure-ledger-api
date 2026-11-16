package io.github.asmeduardo.secureledger.ledger.api;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction " + transactionId + " was not found");
    }
}
