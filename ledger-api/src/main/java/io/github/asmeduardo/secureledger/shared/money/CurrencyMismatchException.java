package io.github.asmeduardo.secureledger.shared.money;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.Currency;

/** Two amounts in different currencies were combined; the ledger never converts currencies. */
public class CurrencyMismatchException extends BusinessException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super(
                ErrorKind.UNPROCESSABLE,
                "currency-mismatch",
                "Currency mismatch",
                "Expected an amount in " + expected + " but got " + actual);
    }
}
