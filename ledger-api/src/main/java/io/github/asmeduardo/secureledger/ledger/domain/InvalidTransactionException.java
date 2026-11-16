package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

/** The proposed transaction breaks a rule of double-entry bookkeeping. */
public class InvalidTransactionException extends BusinessException {

    public InvalidTransactionException(String message) {
        super(ErrorKind.UNPROCESSABLE, "invalid-transaction", "Invalid transaction", message);
    }
}
