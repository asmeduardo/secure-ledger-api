package io.github.asmeduardo.secureledger.ledger.domain;

/** The proposed transaction breaks a rule of double-entry bookkeeping. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
