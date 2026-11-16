package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

/** A posting would leave a customer account with a negative balance. */
public class InsufficientFundsException extends BusinessException {

    private final UUID accountId;

    public InsufficientFundsException(UUID accountId) {
        super(
                ErrorKind.UNPROCESSABLE,
                "insufficient-funds",
                "Insufficient funds",
                "Account " + accountId + " does not have enough funds");
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }
}
