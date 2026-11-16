package io.github.asmeduardo.secureledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerRows;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The double-entry rules and the immutability of posted entries hold in the database itself, even
 * for SQL that bypasses the application.
 */
@IntegrationTest
class LedgerInvariantsIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    LedgerRows rows;

    @BeforeEach
    void setUp() {
        rows = new LedgerRows(jdbc);
    }

    @Test
    void anUnbalancedTransactionIsRejectedWhenItCommits() {
        var transaction = new AtomicReference<UUID>();

        assertThatThrownBy(() -> inTransaction(rows -> {
                    var customer = rows.customerAccount("BRL", "9.99");
                    transaction.set(rows.transaction("DEPOSIT", "BRL", null));
                    rows.entry(transaction.get(), rows.settlementAccount("BRL"), "BRL", "-10.00", null);
                    rows.entry(transaction.get(), customer, "BRL", "9.99", "9.99");
                }))
                .rootCause()
                .hasMessageContaining("not balanced");
        assertThat(count("select count(*) from ledger.ledger_transaction where id = ?", transaction.get()))
                .isZero();
    }

    @Test
    void aTransactionWithoutEntriesIsRejected() {
        assertThatThrownBy(() -> inTransaction(rows -> rows.transaction("DEPOSIT", "BRL", null)))
                .rootCause()
                .hasMessageContaining("at least two entries");
    }

    @Test
    void entriesCannotBeAddedToATransactionAlreadyPosted() {
        var deposit = new AtomicReference<UUID>();
        inTransaction(rows -> deposit.set(rows.deposit(rows.customerAccount("BRL", "3.00"), "BRL", "3.00", "3.00")));

        assertThatThrownBy(() -> inTransaction(rows -> {
                    var first = rows.customerAccount("BRL", "0");
                    var second = rows.customerAccount("BRL", "1.00");
                    rows.entry(deposit.get(), first, "BRL", "-1.00", "0");
                    rows.entry(deposit.get(), second, "BRL", "1.00", "1.00");
                }))
                .rootCause()
                .hasMessageContaining("posted together");
    }

    @Test
    void postedEntriesCannotBeUpdated() {
        var customer = postedDeposit("4.00");

        assertThatThrownBy(() -> jdbc.update("update ledger.entry set amount = 400.00 where account_id = ?", customer))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThat(count("select count(*) from ledger.entry where account_id = ? and amount = 4.00", customer))
                .isOne();
    }

    @Test
    void postedEntriesCannotBeDeleted() {
        var customer = postedDeposit("5.00");

        assertThatThrownBy(() -> jdbc.update("delete from ledger.entry where account_id = ?", customer))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThat(count("select count(*) from ledger.entry where account_id = ?", customer))
                .isOne();
    }

    @Test
    void entriesCannotBeTruncated() {
        postedDeposit("6.00");

        assertThatThrownBy(() -> jdbc.execute("truncate ledger.entry"))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThat(count("select count(*) from ledger.entry")).isPositive();
    }

    @Test
    void postedTransactionsCannotBeChangedOrDeleted() {
        var customer = postedDeposit("7.00");
        var transaction = jdbc.queryForObject(
                "select transaction_id from ledger.entry where account_id = ?", UUID.class, customer);

        assertThatThrownBy(() -> jdbc.update(
                        "update ledger.ledger_transaction set initiated_by = 'someone-else' where id = ?", transaction))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from ledger.ledger_transaction where id = ?", transaction))
                .rootCause()
                .hasMessageContaining("append-only");
    }

    private UUID postedDeposit(String amount) {
        var customer = new AtomicReference<UUID>();
        inTransaction(rows -> {
            customer.set(rows.customerAccount("BRL", amount));
            rows.deposit(customer.get(), "BRL", amount, amount);
        });
        return customer.get();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void inTransaction(Consumer<LedgerRows> work) {
        transactions.executeWithoutResult(status -> work.accept(rows));
    }
}
