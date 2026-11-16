package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

public class ReversalNotAllowedException extends BusinessException {

    public ReversalNotAllowedException(UUID transactionId, String reason) {
        super(
                ErrorKind.UNPROCESSABLE,
                "reversal-not-allowed",
                "Reversal not allowed",
                "Transaction " + transactionId + " cannot be reversed: " + reason);
    }
}
