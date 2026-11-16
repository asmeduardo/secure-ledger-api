package io.github.asmeduardo.secureledger.shared.security;

import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The API client making a request: the subject ({@code sub}) of its access token. It owns the
 * accounts it opens and scopes its idempotency keys. It always comes from a validated token, never
 * from the request body.
 */
public record ClientId(String value) {

    public ClientId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A client id cannot be blank");
        }
    }

    public static ClientId of(Jwt token) {
        return new ClientId(token.getSubject());
    }

    @Override
    public String toString() {
        return value;
    }
}
