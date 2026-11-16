package io.github.asmeduardo.secureledger.transfers.web;

import io.github.asmeduardo.secureledger.shared.money.Money;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Body of a deposit or a withdrawal. */
record AccountMovementRequest(
        @NotNull UUID accountId,

        @NotNull @Positive @Digits(integer = 15, fraction = 2)
        BigDecimal amount,

        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as BRL")
        String currency,

        @Size(max = 140) String reference) {

    Money money() {
        return new Money(amount, Money.currency(currency));
    }
}
