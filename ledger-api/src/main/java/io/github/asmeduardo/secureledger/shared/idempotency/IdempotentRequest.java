package io.github.asmeduardo.secureledger.shared.idempotency;

import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.util.Objects;

/**
 * A write request identified by the client and the key it chose.
 *
 * @param operation name of the use case, part of the fingerprint so a key cannot be reused across
 *     operations
 * @param fingerprint SHA-256 of the canonical request, see {@link RequestFingerprint}
 */
public record IdempotentRequest(ClientId client, String key, String operation, String fingerprint) {

    public IdempotentRequest {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public static IdempotentRequest of(ClientId client, String key, String operation, Object... fields) {
        return new IdempotentRequest(client, key, operation, RequestFingerprint.of(operation, fields));
    }
}
