package io.github.asmeduardo.secureledger.shared.idempotency;

/**
 * @param replayed whether the response was stored by an earlier request with the same key
 */
public record IdempotentResult<T>(T response, boolean replayed) {}
