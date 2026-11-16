package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    @Query("select a from LedgerAccount a where a.type = 'SETTLEMENT' and a.currency = :currency")
    Optional<LedgerAccount> findSettlementAccount(String currency);
}
