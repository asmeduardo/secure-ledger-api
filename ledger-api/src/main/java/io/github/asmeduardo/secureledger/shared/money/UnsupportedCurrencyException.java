package io.github.asmeduardo.secureledger.shared.money;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

/** The ledger only keeps money in the currencies listed in {@link Money#SUPPORTED_CURRENCIES}. */
public class UnsupportedCurrencyException extends BusinessException {

    public UnsupportedCurrencyException(String code) {
        super(
                ErrorKind.INVALID,
                "unsupported-currency",
                "Unsupported currency",
                "Currency " + code + " is not supported; use one of " + Money.SUPPORTED_CURRENCIES);
    }
}
