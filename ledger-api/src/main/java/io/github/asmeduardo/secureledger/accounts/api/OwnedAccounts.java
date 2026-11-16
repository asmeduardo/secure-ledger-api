package io.github.asmeduardo.secureledger.accounts.api;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Ownership checks offered to the other modules. */
public interface OwnedAccounts {

    /**
     * @throws AccountNotFoundException if the account does not exist or belongs to another client;
     *     the two cases are indistinguishable on purpose
     */
    void requireOwnedBy(ClientId client, UUID accountId);

    /** The ids among the given ones that name accounts of the client; any other id is ignored. */
    Set<UUID> ownedAmong(ClientId client, Collection<UUID> accountIds);
}
