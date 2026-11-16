package io.github.asmeduardo.secureledger.ledger.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    @Query("select a from LedgerAccount a where a.type = 'SETTLEMENT' and a.currency = :currency")
    Optional<LedgerAccount> findSettlementAccount(String currency);

    /**
     * Locks the customer accounts among the given ids until the end of the transaction, always in
     * ascending id order. PostgreSQL locks the rows in the order the query returns them, so two
     * transactions touching the same accounts queue up instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccount a where a.id in :ids and a.type = 'CUSTOMER' order by a.id")
    List<LedgerAccount> lockCustomerAccounts(Collection<UUID> ids);
}
