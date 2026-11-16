package io.github.asmeduardo.secureledger.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/**
 * An exact amount in one currency. The amount is always kept at the scale of the currency, so equal
 * values are equal records, and it is never rounded: an amount with more decimal places than the
 * currency has is refused. Amounts may be negative (a debit, or the balance of a settlement
 * account); whether a negative value is acceptable is decided by whoever uses it.
 */
public record Money(BigDecimal amount, Currency currency) {

    /** Currencies the ledger keeps. All of them have two decimal places, as the database columns. */
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("BRL", "USD", "EUR");

    /** Amounts are stored as {@code numeric(19, 2)}: at most 17 integer digits. */
    private static final BigDecimal CAPACITY = BigDecimal.TEN.pow(17);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (!SUPPORTED_CURRENCIES.contains(currency.getCurrencyCode())) {
            throw new UnsupportedCurrencyException(currency.getCurrencyCode());
        }
        int fractionDigits = currency.getDefaultFractionDigits();
        if (amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new InvalidAmountException(
                    amount.toPlainString() + " has more decimal places than " + currency + " allows");
        }
        if (amount.abs().compareTo(CAPACITY) >= 0) {
            throw new InvalidAmountException(amount.toPlainString() + " exceeds the capacity of the ledger");
        }
        amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), currency(currencyCode));
    }

    /** Resolves a supported ISO 4217 code, refusing anything else with the same exception. */
    public static Currency currency(String code) {
        if (code == null || !SUPPORTED_CURRENCIES.contains(code)) {
            throw new UnsupportedCurrencyException(code);
        }
        return Currency.getInstance(code);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
