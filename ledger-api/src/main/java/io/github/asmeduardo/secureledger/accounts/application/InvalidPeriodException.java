package io.github.asmeduardo.secureledger.accounts.application;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

public class InvalidPeriodException extends BusinessException {

    InvalidPeriodException(String message) {
        super(ErrorKind.INVALID, "invalid-period", "Invalid period", message);
    }
}
