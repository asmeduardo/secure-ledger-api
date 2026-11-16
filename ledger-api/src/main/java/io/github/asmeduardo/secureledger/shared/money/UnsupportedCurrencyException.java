package io.github.asmeduardo.secureledger.shared.money;

/** The ledger only keeps money in the currencies listed in {@link Money#SUPPORTED_CURRENCIES}. */
public class UnsupportedCurrencyException extends IllegalArgumentException {

    public UnsupportedCurrencyException(String code) {
        super("Currency " + code + " is not supported; use one of " + Money.SUPPORTED_CURRENCIES);
    }
}
