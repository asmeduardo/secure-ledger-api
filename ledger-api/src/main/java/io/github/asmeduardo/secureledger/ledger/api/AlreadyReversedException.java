package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

public class AlreadyReversedException extends BusinessException {

    public AlreadyReversedException(UUID transactionId) {
        super(
                ErrorKind.CONFLICT,
                "already-reversed",
                "Already reversed",
                "Transaction " + transactionId + " has already been reversed");
    }
}
