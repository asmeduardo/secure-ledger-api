package io.github.asmeduardo.secureledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerRows;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Column and key constraints of the ledger schema, exercised with plain SQL. */
@IntegrationTest
class LedgerSchemaIT {

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
    void everySupportedCurrencyHasExactlyOneSettlementAccount() {
        assertThat(jdbc.queryForList(
                        "select currency from ledger.ledger_account where type = 'SETTLEMENT' order by currency",
                        String.class))
                .containsExactly("BRL", "EUR", "USD");
    }

    @Test
    void acceptsABalancedTransactionWithItsEntries() {
        var customer = new AtomicReference<UUID>();
        inTransaction(rows -> {
            customer.set(rows.customerAccount("BRL", "10.00"));
            rows.deposit(customer.get(), "BRL", "10.00", "10.00");
        });

        assertThat(jdbc.queryForObject(
                        "select sum(amount) from ledger.entry where account_id = ?", BigDecimal.class, customer.get()))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void onlySupportedCurrenciesAreKept() {
        assertRejected(rows -> rows.customerAccount("JPY", "0"), "ck_ledger_account_currency");
    }

    @Test
    void customerBalancesAreNeverNegative() {
        assertRejected(rows -> rows.customerAccount("BRL", "-0.01"), "ck_ledger_account_balance");
    }

    @Test
    void anEntryMustBeInTheCurrencyOfItsAccount() {
        assertRejected(
                rows -> {
                    var transaction = rows.transaction("DEPOSIT", "BRL", null);
                    rows.entry(transaction, rows.settlementAccount("USD"), "BRL", "-1.00", null);
                },
                "fk_entry_account");
    }

    @Test
    void anEntryMustBeInTheCurrencyOfItsTransaction() {
        assertRejected(
                rows -> {
                    var transaction = rows.transaction("DEPOSIT", "BRL", null);
                    rows.entry(transaction, rows.settlementAccount("USD"), "USD", "-1.00", null);
                },
                "fk_entry_transaction");
    }

    @Test
    void entriesHaveNonZeroAmounts() {
        assertRejected(
                rows -> {
                    var transaction = rows.transaction("DEPOSIT", "BRL", null);
                    rows.entry(transaction, rows.settlementAccount("BRL"), "BRL", "0.00", null);
                },
                "ck_entry_amount");
    }

    @Test
    void anAccountAppearsOnceInATransaction() {
        assertRejected(
                rows -> {
                    var customer = rows.customerAccount("BRL", "0");
                    var transaction = rows.transaction("TRANSFER", "BRL", null);
                    rows.entry(transaction, customer, "BRL", "-1.00", "0");
                    rows.entry(transaction, customer, "BRL", "1.00", "0");
                },
                "uk_entry_transaction_account");
    }

    @Test
    void aReversalNamesTheTransactionItReverses() {
        assertRejected(rows -> rows.transaction("REVERSAL", "BRL", null), "ck_ledger_transaction_reversal");
    }

    @Test
    void aTransactionIsReversedAtMostOnce() {
        assertRejected(
                rows -> {
                    var customer = rows.customerAccount("BRL", "0");
                    var deposit = rows.deposit(customer, "BRL", "5.00", "5.00");
                    rows.transaction("REVERSAL", "BRL", deposit);
                    rows.transaction("REVERSAL", "BRL", deposit);
                },
                "uk_ledger_transaction_reverses");
    }

    private Object inTransaction(Consumer<LedgerRows> work) {
        return transactions.execute(status -> {
            work.accept(rows);
            return null;
        });
    }

    private void assertRejected(Consumer<LedgerRows> work, String constraint) {
        assertThatThrownBy(() -> inTransaction(work)).hasMessageContaining(constraint);
    }
}
