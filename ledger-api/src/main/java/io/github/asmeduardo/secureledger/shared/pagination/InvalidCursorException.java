package io.github.asmeduardo.secureledger.shared.pagination;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

public class InvalidCursorException extends BusinessException {

    public InvalidCursorException() {
        super(
                ErrorKind.INVALID,
                "invalid-cursor",
                "Invalid cursor",
                "The cursor was not returned by this API; start again without it.");
    }
}
