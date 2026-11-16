package io.github.asmeduardo.secureledger.ledger.api;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

public class TransactionNotFoundException extends BusinessException {

    public TransactionNotFoundException(UUID transactionId) {
        super(
                ErrorKind.NOT_FOUND,
                "transaction-not-found",
                "Transaction not found",
                "Transaction " + transactionId + " was not found");
    }
}
