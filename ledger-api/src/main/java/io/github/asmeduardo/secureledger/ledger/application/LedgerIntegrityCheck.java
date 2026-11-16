package io.github.asmeduardo.secureledger.ledger.application;

import io.github.asmeduardo.secureledger.ledger.api.LedgerIntegrity;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full scan of the ledger. Fine at the size of this project; a large ledger would check only the
 * entries posted since the last run, keeping a watermark on the entry id.
 */
@Service
class LedgerIntegrityCheck implements LedgerIntegrity {

    private final JdbcTemplate jdbc;

    LedgerIntegrityCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IntegrityReport check() {
        var accounts =
                jdbc.queryForObject("select count(*) from ledger.ledger_account where type = 'CUSTOMER'", Long.class);
        var transactions = jdbc.queryForObject("select count(*) from ledger.ledger_transaction", Long.class);
        var divergences = jdbc.query(
                """
                select a.id, a.currency, a.balance, coalesce(sum(e.amount), 0) as ledger_balance
                  from ledger.ledger_account a
                  left join ledger.entry e on e.account_id = a.id
                 where a.type = 'CUSTOMER'
                 group by a.id, a.currency, a.balance
                having a.balance <> coalesce(sum(e.amount), 0)
                 order by a.id
                """,
                (row, index) -> new BalanceDivergence(
                        row.getObject("id", UUID.class),
                        row.getString("currency"),
                        row.getBigDecimal("balance"),
                        row.getBigDecimal("ledger_balance")));
        var unbalanced = jdbc.query(
                """
                select t.id, count(e.id) as entry_count, coalesce(sum(e.amount), 0) as entry_sum
                  from ledger.ledger_transaction t
                  left join ledger.entry e on e.transaction_id = t.id
                 group by t.id
                having count(e.id) < 2 or coalesce(sum(e.amount), 0) <> 0
                 order by t.id
                """,
                (row, index) -> new UnbalancedTransaction(
                        row.getObject("id", UUID.class), row.getInt("entry_count"), row.getBigDecimal("entry_sum")));
        return new IntegrityReport(accounts, transactions, divergences, unbalanced);
    }
}
