package io.github.asmeduardo.secureledger.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    private static final UUID ACCOUNT = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Test
    void isTheHexSha256OfTheRequest() {
        assertThat(RequestFingerprint.of("DEPOSIT", ACCOUNT, Money.of("10", "BRL"), null))
                .matches("[0-9a-f]{64}")
                .isEqualTo(RequestFingerprint.of("DEPOSIT", ACCOUNT, Money.of("10", "BRL"), null));
    }

    @Test
    void ignoresTrailingZerosOfAmounts() {
        assertThat(RequestFingerprint.of("DEPOSIT", Money.of("10.50", "BRL")))
                .isEqualTo(RequestFingerprint.of("DEPOSIT", Money.of("10.5", "BRL")));
        assertThat(RequestFingerprint.of("DEPOSIT", new BigDecimal("1.0")))
                .isEqualTo(RequestFingerprint.of("DEPOSIT", new BigDecimal("1")));
    }

    @Test
    void changesWithTheOperationTheAmountOrTheCurrency() {
        var deposit = RequestFingerprint.of("DEPOSIT", ACCOUNT, Money.of("10", "BRL"));

        assertThat(RequestFingerprint.of("WITHDRAWAL", ACCOUNT, Money.of("10", "BRL")))
                .isNotEqualTo(deposit);
        assertThat(RequestFingerprint.of("DEPOSIT", ACCOUNT, Money.of("10.01", "BRL")))
                .isNotEqualTo(deposit);
        assertThat(RequestFingerprint.of("DEPOSIT", ACCOUNT, Money.of("10", "USD")))
                .isNotEqualTo(deposit);
    }

    @Test
    void tellsAMissingValueFromAnEmptyOneAndKeepsFieldsApart() {
        assertThat(RequestFingerprint.of("DEPOSIT", (Object) null)).isNotEqualTo(RequestFingerprint.of("DEPOSIT", ""));
        assertThat(RequestFingerprint.of("TRANSFER", "ab", "c"))
                .isNotEqualTo(RequestFingerprint.of("TRANSFER", "a", "bc"));
    }
}
