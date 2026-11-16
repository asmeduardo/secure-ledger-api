package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    @EntityGraph(attributePaths = "entries")
    Optional<LedgerTransaction> findWithEntriesById(UUID id);

    boolean existsByReversesId(UUID reversesId);
}
