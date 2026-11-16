package io.github.asmeduardo.secureledger.accounts.api;

import io.github.asmeduardo.secureledger.shared.error.BusinessException;
import io.github.asmeduardo.secureledger.shared.error.ErrorKind;
import java.util.UUID;

/** Raised alike for an unknown account and for an account of another client. */
public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(UUID accountId) {
        super(ErrorKind.NOT_FOUND, "account-not-found", "Account not found", "Account " + accountId + " was not found");
    }
}
