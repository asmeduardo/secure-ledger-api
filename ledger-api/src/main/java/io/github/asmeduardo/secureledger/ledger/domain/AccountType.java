package io.github.asmeduardo.secureledger.ledger.domain;

public enum AccountType {
    /** Money held for an API client. Its balance is kept with every posting and never goes negative. */
    CUSTOMER,
    /**
     * Counterpart of money entering or leaving the ledger (deposits and withdrawals), one per
     * currency. Its balance is the opposite of all customer money in that currency, so it is negative
     * by design; it is derived from the entries and not kept on the account row.
     */
    SETTLEMENT
}
