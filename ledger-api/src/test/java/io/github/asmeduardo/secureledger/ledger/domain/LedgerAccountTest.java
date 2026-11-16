package io.github.asmeduardo.secureledger.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.shared.money.CurrencyMismatchException;
import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Test
    void customerAccountOpensWithAZeroBalanceInItsCurrency() {
        var account = LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);

        assertThat(account.getType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.getCurrency()).isEqualTo(Money.currency("BRL"));
        assertThat(account.balance()).contains(Money.of("0", "BRL"));
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void creditsAndDebitsChangeTheCustomerBalance() {
        var account = customer("BRL");

        assertThat(account.apply(Money.of("100.00", "BRL"))).contains(Money.of("100.00", "BRL"));
        assertThat(account.apply(Money.of("-99.99", "BRL"))).contains(Money.of("0.01", "BRL"));
        assertThat(account.balance()).contains(Money.of("0.01", "BRL"));
    }

    @Test
    void customerBalanceMayReachZeroButNeverGoesNegative() {
        var account = customer("BRL");
        account.apply(Money.of("10.00", "BRL"));

        assertThatThrownBy(() -> account.apply(Money.of("-10.01", "BRL")))
                .isInstanceOf(InsufficientFundsException.class)
                .extracting("accountId")
                .isEqualTo(account.getId());
        assertThat(account.balance()).contains(Money.of("10.00", "BRL"));
        assertThat(account.apply(Money.of("-10.00", "BRL"))).contains(Money.of("0.00", "BRL"));
    }

    @Test
    void balanceAfterPreviewsAPostingWithoutApplyingIt() {
        var account = customer("USD");
        account.apply(Money.of("5", "USD"));

        assertThat(account.balanceAfter(Money.of("-2", "USD"))).isEqualTo(Money.of("3", "USD"));
        assertThat(account.balance()).contains(Money.of("5", "USD"));
        assertThatThrownBy(() -> account.balanceAfter(Money.of("-6", "USD")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void refusesAnAmountInAnotherCurrency() {
        var account = customer("BRL");

        assertThatThrownBy(() -> account.apply(Money.of("1", "USD"))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void settlementAccountMayGoNegativeAndKeepsNoBalanceOfItsOwn() {
        var settlement = LedgerAccount.settlementAccount(UUID.randomUUID(), Money.currency("BRL"), NOW);

        assertThat(settlement.apply(Money.of("-1000000.00", "BRL"))).isEmpty();
        assertThat(settlement.balance()).isEmpty();
        assertThatThrownBy(() -> settlement.apply(Money.of("-1", "EUR"))).isInstanceOf(CurrencyMismatchException.class);
    }

    private static LedgerAccount customer(String currency) {
        return LedgerAccount.openCustomerAccount(UUID.randomUUID(), Money.currency(currency), NOW);
    }
}
