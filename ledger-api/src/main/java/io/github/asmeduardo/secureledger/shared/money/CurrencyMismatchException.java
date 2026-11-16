package io.github.asmeduardo.secureledger.shared.money;

import java.util.Currency;

/** Two amounts in different currencies were combined; the ledger never converts currencies. */
public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Expected an amount in " + expected + " but got " + actual);
    }
}
