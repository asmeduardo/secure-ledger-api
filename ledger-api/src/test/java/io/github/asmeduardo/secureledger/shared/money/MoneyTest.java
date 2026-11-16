package io.github.asmeduardo.secureledger.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void keepsTheAmountAtTheScaleOfTheCurrency() {
        assertThat(Money.of("10.5", "BRL").amount())
                .isEqualByComparingTo("10.50")
                .hasToString("10.50");
        assertThat(Money.of("7", "USD").amount()).hasToString("7.00");
    }

    @Test
    void equalValuesWrittenWithDifferentScalesAreEqual() {
        assertThat(Money.of("1.000", "BRL")).isEqualTo(Money.of("1", "BRL"));
    }

    @Test
    void neverRoundsAnAmountWithMoreDecimalPlacesThanTheCurrencyHas() {
        assertThatThrownBy(() -> Money.of("10.001", "BRL"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("10.001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"JPY", "BHD", "XAU"})
    void acceptsOnlyTheSupportedCurrencies(String code) {
        assertThatThrownBy(() -> Money.of("1", code))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining(code);
    }

    @Test
    void rejectsACodeThatIsNotACurrency() {
        assertThatThrownBy(() -> Money.currency("REAL")).isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void addsAndSubtractsInTheSameCurrency() {
        var balance = Money.of("100.00", "BRL");

        assertThat(balance.plus(Money.of("0.10", "BRL"))).isEqualTo(Money.of("100.10", "BRL"));
        assertThat(balance.minus(Money.of("100.01", "BRL"))).isEqualTo(Money.of("-0.01", "BRL"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "BRL").plus(Money.of("1", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of("1", "BRL").minus(Money.of("1", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void knowsItsSign() {
        assertThat(Money.of("-3", "BRL").isNegative()).isTrue();
        assertThat(Money.of("-3", "BRL").negate()).isEqualTo(Money.of("3", "BRL"));
        assertThat(Money.zero(USD).isZero()).isTrue();
        assertThat(Money.of("0.01", "USD").isPositive()).isTrue();
    }

    @Test
    void refusesAmountsBeyondTheCapacityOfTheLedger() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1E17"), BRL)).isInstanceOf(InvalidAmountException.class);
        assertThat(new Money(new BigDecimal("99999999999999999.99"), BRL).isPositive())
                .isTrue();
    }

    @Test
    void printsAmountAndCurrency() {
        assertThat(Money.of("1234.5", "EUR")).hasToString("1234.50 EUR");
    }
}
