package io.github.asmeduardo.secureledger.shared.idempotency;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException() {
        super(
                ErrorKind.UNPROCESSABLE,
                "idempotency-key-reused",
                "Idempotency key reused",
                "This Idempotency-Key was already used for a different request.");
    }
}
