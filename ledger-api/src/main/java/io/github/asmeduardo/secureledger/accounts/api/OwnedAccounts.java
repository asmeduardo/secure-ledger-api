package io.github.asmeduardo.secureledger.accounts.api;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.UUID;

/** Ownership checks offered to the other modules. */
public interface OwnedAccounts {

    /**
     * @throws AccountNotFoundException if the account does not exist or belongs to another client;
     *     the two cases are indistinguishable on purpose
     */
    void requireOwnedBy(ClientId client, UUID accountId);
}
