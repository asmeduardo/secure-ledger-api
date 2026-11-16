package io.github.asmeduardo.secureledger.shared.money;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

/** The amount cannot be represented exactly in its currency or does not fit in the ledger. */
public class InvalidAmountException extends BusinessException {

    public InvalidAmountException(String message) {
        super(ErrorKind.INVALID, "invalid-amount", "Invalid amount", message);
    }
}
