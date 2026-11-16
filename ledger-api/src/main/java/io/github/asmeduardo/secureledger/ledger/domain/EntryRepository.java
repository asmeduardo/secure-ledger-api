package io.github.asmeduardo.secureledger.ledger.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    /**
     * Entries of an account strictly below an entry id, newest first, with their transactions.
     * Served by the index on {@code (account_id, id)}.
     */
    @Query("""
            select e from Entry e join fetch e.transaction
             where e.accountId = :accountId and e.id < :beforeId
             order by e.id desc
            """)
    List<Entry> statementPage(UUID accountId, long beforeId, Limit limit);
}
