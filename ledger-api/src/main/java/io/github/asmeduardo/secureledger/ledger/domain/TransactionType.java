package io.github.asmeduardo.secureledger.ledger.domain;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    /** Compensates an earlier transaction; the only way to correct the ledger. */
    REVERSAL
}
