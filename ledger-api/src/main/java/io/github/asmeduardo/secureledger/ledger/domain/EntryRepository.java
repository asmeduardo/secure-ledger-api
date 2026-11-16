package io.github.asmeduardo.secureledger.ledger.domain;

import java.time.Instant;
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

    /** Entries of an account posted in a period, oldest first, within a window of entry ids. */
    @Query("""
            select e from Entry e join fetch e.transaction
             where e.accountId = :accountId and e.createdAt >= :from and e.createdAt < :to
               and e.id > :afterId and e.id <= :upToId
             order by e.id asc
            """)
    List<Entry> periodPage(UUID accountId, Instant from, Instant to, long afterId, long upToId, Limit limit);

    @Query("select coalesce(max(e.id), 0) from Entry e where e.accountId = :accountId")
    long latestEntryId(UUID accountId);
}
