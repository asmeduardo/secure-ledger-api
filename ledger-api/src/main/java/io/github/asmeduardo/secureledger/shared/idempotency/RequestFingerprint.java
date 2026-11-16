package io.github.asmeduardo.secureledger.shared.idempotency;

import io.github.asmeduardo.secureledger.shared.money.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a canonical form of a request, used to tell a retry from a different request that
 * reuses an idempotency key. Trailing zeros in amounts do not change the meaning of a request, so
 * they do not change the fingerprint either; a missing value differs from an empty one.
 */
public final class RequestFingerprint {

    private static final char SEPARATOR = '\u001f';
    private static final String MISSING = "\u0000";

    private RequestFingerprint() {}

    public static String of(String operation, Object... fields) {
        var canonical = new StringBuilder(operation);
        for (var field : fields) {
            canonical.append(SEPARATOR).append(canonical(field));
        }
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    private static String canonical(Object field) {
        return switch (field) {
            case null -> MISSING;
            case BigDecimal amount -> amount.stripTrailingZeros().toPlainString();
            case Money money ->
                money.amount().stripTrailingZeros().toPlainString() + " "
                        + money.currency().getCurrencyCode();
            default -> field.toString();
        };
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform must support SHA-256", e);
        }
    }
}
