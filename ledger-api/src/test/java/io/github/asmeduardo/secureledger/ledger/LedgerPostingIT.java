package io.github.asmeduardo.secureledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.ledger.api.AlreadyReversedException;
import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.ledger.api.PostedEntry;
import io.github.asmeduardo.secureledger.ledger.api.TransactionKind;
import io.github.asmeduardo.secureledger.ledger.api.TransactionNotFoundException;
import io.github.asmeduardo.secureledger.ledger.domain.InsufficientFundsException;
import io.github.asmeduardo.secureledger.ledger.domain.InvalidTransactionException;
import io.github.asmeduardo.secureledger.shared.money.CurrencyMismatchException;
import io.github.asmeduardo.secureledger.shared.money.InvalidAmountException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import io.github.asmeduardo.secureledger.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class LedgerPostingIT {

    private static final UUID BRL_SETTLEMENT = UUID.fromString("00000000-0000-4000-8000-000000000986");

    @Autowired
    Ledger ledger;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void depositCreditsTheAccountAgainstTheSettlementAccount() {
        var account = open("BRL");

        var deposit = ledger.deposit(account, brl("100.00"), "wallet-app", "top-up 42");

        assertThat(deposit.kind()).isEqualTo(TransactionKind.DEPOSIT);
        assertThat(deposit.amount()).isEqualTo(brl("100.00"));
        assertThat(deposit.initiatedBy()).isEqualTo("wallet-app");
        assertThat(deposit.reference()).contains("top-up 42");
        assertThat(deposit.entries())
                .containsExactly(
                        new PostedEntry(BRL_SETTLEMENT, brl("-100.00"), Optional.empty()),
                        new PostedEntry(account, brl("100.00"), Optional.of(brl("100.00"))));
        assertThat(storedBalance(account)).isEqualByComparingTo("100.00");
        assertThat(sumOfEntries(account)).isEqualByComparingTo("100.00");
        assertThat(ledger.find(deposit.id())).contains(deposit);
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        var source = open("BRL");
        var destination = open("BRL");
        ledger.deposit(source, brl("50.00"), "wallet-app", null);

        var transfer = ledger.transfer(source, destination, brl("20.00"), "wallet-app", null);

        assertThat(transfer.kind()).isEqualTo(TransactionKind.TRANSFER);
        assertThat(transfer.entries())
                .containsExactly(
                        new PostedEntry(source, brl("-20.00"), Optional.of(brl("30.00"))),
                        new PostedEntry(destination, brl("20.00"), Optional.of(brl("20.00"))));
        assertThat(ledger.balances(List.of(source, destination, UUID.randomUUID())))
                .containsOnlyKeys(source, destination)
                .containsEntry(source, brl("30.00"))
                .containsEntry(destination, brl("20.00"));
    }

    @Test
    void withdrawalBeyondTheBalanceIsRefusedAndLeavesNoTrace() {
        var account = open("BRL");
        ledger.deposit(account, brl("10.00"), "wallet-app", null);
        var transactionsBefore = countTransactions();

        assertThatThrownBy(() -> ledger.withdraw(account, brl("10.01"), "wallet-app", null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(storedBalance(account)).isEqualByComparingTo("10.00");
        assertThat(countTransactions()).isEqualTo(transactionsBefore);
        var withdrawal = ledger.withdraw(account, brl("10.00"), "wallet-app", null);
        assertThat(withdrawal.entries().getFirst())
                .isEqualTo(new PostedEntry(account, brl("-10.00"), Optional.of(brl("0.00"))));
    }

    @Test
    void movementsStayInOneCurrency() {
        var reais = open("BRL");
        var dollars = open("USD");
        ledger.deposit(reais, brl("5.00"), "wallet-app", null);

        assertThatThrownBy(() -> ledger.transfer(reais, dollars, brl("1.00"), "wallet-app", null))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> ledger.deposit(dollars, brl("1.00"), "wallet-app", null))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void amountsMustBePositiveAndAccountsDistinct() {
        var account = open("BRL");

        assertThatThrownBy(() -> ledger.deposit(account, brl("-1.00"), "wallet-app", null))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> ledger.deposit(account, brl("0"), "wallet-app", null))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> ledger.transfer(account, account, brl("1.00"), "wallet-app", null))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void reversalCompensatesATransactionOnlyOnce() {
        var account = open("BRL");
        var deposit = ledger.deposit(account, brl("40.00"), "wallet-app", null);

        var reversal = ledger.reverse(deposit.id(), "wallet-app", "sent twice");

        assertThat(reversal.kind()).isEqualTo(TransactionKind.REVERSAL);
        assertThat(reversal.reversesId()).contains(deposit.id());
        assertThat(reversal.amount()).isEqualTo(brl("40.00"));
        assertThat(storedBalance(account)).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> ledger.reverse(deposit.id(), "wallet-app", null))
                .isInstanceOf(AlreadyReversedException.class);
    }

    @Test
    void unknownTransactionCannotBeFoundOrReversed() {
        var unknown = UUID.randomUUID();

        assertThat(ledger.find(unknown)).isEmpty();
        assertThatThrownBy(() -> ledger.reverse(unknown, "wallet-app", null))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    private UUID open(String currency) {
        var id = UUID.randomUUID();
        ledger.openCustomerAccount(id, Money.currency(currency));
        return id;
    }

    private BigDecimal storedBalance(UUID account) {
        return jdbc.queryForObject("select balance from ledger.ledger_account where id = ?", BigDecimal.class, account);
    }

    private BigDecimal sumOfEntries(UUID account) {
        return jdbc.queryForObject(
                "select sum(amount) from ledger.entry where account_id = ?", BigDecimal.class, account);
    }

    private long countTransactions() {
        return jdbc.queryForObject("select count(*) from ledger.ledger_transaction", Long.class);
    }

    private static Money brl(String amount) {
        return Money.of(amount, "BRL");
    }
}
