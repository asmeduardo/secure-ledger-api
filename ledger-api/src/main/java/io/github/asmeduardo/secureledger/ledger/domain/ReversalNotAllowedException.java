package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.UUID;

public class ReversalNotAllowedException extends RuntimeException {

    public ReversalNotAllowedException(UUID transactionId, String reason) {
        super("Transaction " + transactionId + " cannot be reversed: " + reason);
    }
}
