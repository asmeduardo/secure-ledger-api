package io.github.asmeduardo.secureledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.shared.money.CurrencyMismatchException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LedgerTransactionTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    private final LedgerAccount settlement =
            LedgerAccount.settlementAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);
    private final LedgerAccount alice =
            LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);
    private final LedgerAccount bob = LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);

    @Test
    void depositDebitsSettlementAndCreditsTheCustomer() {
        var deposit = post(
                TransactionType.DEPOSIT,
                Leg.debit(settlement.getId(), brl("100.00")),
                Leg.credit(alice.getId(), brl("100.00")));

        assertThat(deposit.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(deposit.getCurrency()).isEqualTo(Money.currency("BRL"));
        assertThat(deposit.getInitiatedBy()).isEqualTo("wallet-app");
        assertThat(deposit.getCreatedAt()).isEqualTo(NOW);
        assertThat(deposit.getEntries()).hasSize(2);
        var settlementEntry = deposit.getEntries().get(0);
        var aliceEntry = deposit.getEntries().get(1);
        assertThat(settlementEntry.amount()).isEqualTo(brl("-100.00"));
        assertThat(settlementEntry.balanceAfter()).isEmpty();
        assertThat(aliceEntry.getAccountId()).isEqualTo(alice.getId());
        assertThat(aliceEntry.amount()).isEqualTo(brl("100.00"));
        assertThat(aliceEntry.balanceAfter()).contains(brl("100.00"));
        assertThat(aliceEntry.getTransaction()).isSameAs(deposit);
        assertThat(aliceEntry.getCreatedAt()).isEqualTo(NOW);
        assertThat(alice.balance()).contains(brl("100.00"));
    }

    @Test
    void transferMovesMoneyBetweenCustomers() {
        fund(alice, "30.00");

        var transfer = post(
                TransactionType.TRANSFER,
                Leg.debit(alice.getId(), brl("12.50")),
                Leg.credit(bob.getId(), brl("12.50")));

        assertThat(transfer.legs())
                .containsExactly(new Leg(alice.getId(), brl("-12.50")), new Leg(bob.getId(), brl("12.50")));
        assertThat(alice.balance()).contains(brl("17.50"));
        assertThat(bob.balance()).contains(brl("12.50"));
    }

    @Test
    void needsAtLeastTwoEntries() {
        assertThatThrownBy(() -> post(TransactionType.DEPOSIT, Leg.credit(alice.getId(), brl("1"))))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("at least two");
    }

    @Test
    void entriesMustSumToZero() {
        assertThatThrownBy(() -> post(
                        TransactionType.DEPOSIT,
                        Leg.debit(settlement.getId(), brl("100.00")),
                        Leg.credit(alice.getId(), brl("99.99"))))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("sum to zero");
        assertThat(alice.balance()).contains(brl("0"));
    }

    @Test
    void entriesMustShareOneCurrency() {
        var dollars = LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency("USD"), NOW);

        assertThatThrownBy(() -> LedgerTransaction.post(
                        UUID.randomUUID(),
                        TransactionType.TRANSFER,
                        List.of(Leg.debit(alice.getId(), brl("1")), Leg.credit(dollars.getId(), Money.of("1", "USD"))),
                        index(alice, dollars),
                        "wallet-app",
                        null,
                        NOW))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void anEntryMustBeInTheCurrencyOfItsAccount() {
        var usdSettlement = LedgerAccount.settlementAccount(UUID.randomUUID(), Money.currency("USD"), NOW);

        assertThatThrownBy(() -> LedgerTransaction.post(
                        UUID.randomUUID(),
                        TransactionType.DEPOSIT,
                        List.of(
                                Leg.debit(usdSettlement.getId(), Money.of("1", "USD")),
                                Leg.credit(alice.getId(), Money.of("1", "USD"))),
                        index(usdSettlement, alice),
                        "wallet-app",
                        null,
                        NOW))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void zeroAmountsAreNotEntries() {
        assertThatThrownBy(() -> post(
                        TransactionType.TRANSFER,
                        Leg.debit(alice.getId(), brl("0")),
                        Leg.credit(bob.getId(), brl("0"))))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void anAccountAppearsOnlyOncePerTransaction() {
        fund(alice, "5");

        assertThatThrownBy(() -> post(
                        TransactionType.TRANSFER,
                        Leg.debit(alice.getId(), brl("5")),
                        Leg.credit(alice.getId(), brl("5"))))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void everyAccountMustBeKnown() {
        fund(alice, "5");

        assertThatThrownBy(() -> post(
                        TransactionType.TRANSFER,
                        Leg.debit(alice.getId(), brl("5")),
                        Leg.credit(UUID.randomUUID(), brl("5"))))
                .isInstanceOf(UnknownLedgerAccountException.class);
    }

    @Test
    void insufficientFundsInOneAccountLeavesEveryAccountUntouched() {
        fund(alice, "10.00");
        var carol = LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);

        assertThatThrownBy(() -> LedgerTransaction.post(
                        UUID.randomUUID(),
                        TransactionType.TRANSFER,
                        List.of(
                                Leg.credit(bob.getId(), brl("15.00")),
                                Leg.debit(alice.getId(), brl("10.00")),
                                Leg.debit(carol.getId(), brl("5.00"))),
                        index(alice, bob, carol),
                        "wallet-app",
                        null,
                        NOW))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(alice.balance()).contains(brl("10.00"));
        assertThat(bob.balance()).contains(brl("0"));
    }

    @Test
    void reversalIsOnlyCreatedFromTheTransactionItReverses() {
        assertThatThrownBy(() -> post(
                        TransactionType.REVERSAL,
                        Leg.debit(settlement.getId(), brl("1")),
                        Leg.credit(alice.getId(), brl("1"))))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void reversalPostsTheOppositeEntries() {
        var deposit = post(
                TransactionType.DEPOSIT,
                Leg.debit(settlement.getId(), brl("40")),
                Leg.credit(alice.getId(), brl("40")));

        var reversal = deposit.reverse(UUID.randomUUID(), index(settlement, alice), "wallet-app", "duplicated", NOW);

        assertThat(reversal.getType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getReversesId()).contains(deposit.getId());
        assertThat(reversal.getReference()).contains("duplicated");
        assertThat(reversal.legs())
                .containsExactly(new Leg(settlement.getId(), brl("40")), new Leg(alice.getId(), brl("-40")));
        assertThat(alice.balance()).contains(brl("0"));
    }

    @Test
    void reversalStillRespectsTheBalanceOfTheAccounts() {
        var deposit = post(
                TransactionType.DEPOSIT,
                Leg.debit(settlement.getId(), brl("40")),
                Leg.credit(alice.getId(), brl("40")));
        post(TransactionType.TRANSFER, Leg.debit(alice.getId(), brl("30")), Leg.credit(bob.getId(), brl("30")));

        assertThatThrownBy(() -> deposit.reverse(UUID.randomUUID(), index(settlement, alice), "wallet-app", null, NOW))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void aReversalCannotBeReversed() {
        var deposit = post(
                TransactionType.DEPOSIT,
                Leg.debit(settlement.getId(), brl("40")),
                Leg.credit(alice.getId(), brl("40")));
        var reversal = deposit.reverse(UUID.randomUUID(), index(settlement, alice), "wallet-app", null, NOW);

        assertThatThrownBy(() -> reversal.reverse(UUID.randomUUID(), index(settlement, alice), "wallet-app", null, NOW))
                .isInstanceOf(ReversalNotAllowedException.class);
    }

    @Test
    void recordsWhoInitiatedItAndAShortReference() {
        var legs = List.of(Leg.debit(settlement.getId(), brl("1")), Leg.credit(alice.getId(), brl("1")));

        assertThatThrownBy(() -> LedgerTransaction.post(
                        UUID.randomUUID(), TransactionType.DEPOSIT, legs, index(settlement, alice), " ", null, NOW))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> LedgerTransaction.post(
                        UUID.randomUUID(),
                        TransactionType.DEPOSIT,
                        legs,
                        index(settlement, alice),
                        "wallet-app",
                        "x".repeat(141),
                        NOW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    private LedgerTransaction post(TransactionType type, Leg... legs) {
        return LedgerTransaction.post(
                UUID.randomUUID(), type, List.of(legs), index(settlement, alice, bob), "wallet-app", null, NOW);
    }

    private void fund(LedgerAccount account, String amount) {
        LedgerTransaction.post(
                UUID.randomUUID(),
                TransactionType.DEPOSIT,
                List.of(Leg.debit(settlement.getId(), brl(amount)), Leg.credit(account.getId(), brl(amount))),
                index(settlement, account),
                "wallet-app",
                null,
                NOW);
    }

    static Map<UUID, LedgerAccount> index(LedgerAccount... accounts) {
        return Stream.of(accounts).collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
    }

    private static Money brl(String amount) {
        return Money.of(amount, "BRL");
    }
}
