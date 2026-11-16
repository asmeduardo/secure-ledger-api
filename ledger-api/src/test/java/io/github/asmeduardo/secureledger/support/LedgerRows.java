package io.github.asmeduardo.secureledger.support;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes ledger rows with plain SQL, bypassing the application, to prove what the database itself
 * accepts and rejects.
 */
public final class LedgerRows {

    private final JdbcTemplate jdbc;

    public LedgerRows(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID settlementAccount(String currency) {
        return jdbc.queryForObject(
                "select id from ledger.ledger_account where type = 'SETTLEMENT' and currency = ?",
                UUID.class,
                currency);
    }

    public UUID customerAccount(String currency, String balance) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into ledger.ledger_account (id, currency, type, balance, created_at) values (?, ?, 'CUSTOMER', ?, ?)",
                id,
                currency,
                new BigDecimal(balance),
                now());
        return id;
    }

    public UUID transaction(String type, String currency, UUID reverses) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into ledger.ledger_transaction (id, type, currency, initiated_by, reverses_id, created_at)
                values (?, ?, ?, 'schema-test', ?, ?)
                """, id, type, currency, reverses, now());
        return id;
    }

    public void entry(UUID transaction, UUID account, String currency, String amount, String balanceAfter) {
        jdbc.update(
                """
                insert into ledger.entry (transaction_id, account_id, currency, amount, balance_after, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                transaction,
                account,
                currency,
                new BigDecimal(amount),
                balanceAfter == null ? null : new BigDecimal(balanceAfter),
                now());
    }

    /** A deposit posted correctly: settlement debited, customer credited, balance kept in step. */
    public UUID deposit(UUID customer, String currency, String amount, String balanceAfter) {
        var transaction = transaction("DEPOSIT", currency, null);
        entry(transaction, settlementAccount(currency), currency, "-" + amount, null);
        entry(transaction, customer, currency, amount, balanceAfter);
        return transaction;
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
