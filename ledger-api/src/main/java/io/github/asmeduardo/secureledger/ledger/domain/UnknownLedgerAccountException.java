package io.github.asmeduardo.secureledger.ledger.domain;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

public class UnknownLedgerAccountException extends BusinessException {

    public UnknownLedgerAccountException(UUID accountId) {
        // Same problem as an account of another client: the response never reveals which accounts exist.
        super(ErrorKind.NOT_FOUND, "account-not-found", "Account not found", "Account " + accountId + " was not found");
    }
}
