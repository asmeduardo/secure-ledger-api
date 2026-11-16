package io.github.asmeduardo.secureledger.shared.error;

import java.util.Objects;

/**
 * A request that breaks a business rule. Each subclass names a stable problem code, documented in
 * the API as {@code urn:secure-ledger:problem:<code>}, and a kind that the web layer translates into
 * an HTTP status. The domain stays free of HTTP concerns.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorKind kind;
    private final String code;
    private final String title;

    protected BusinessException(ErrorKind kind, String code, String title, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = Objects.requireNonNull(code, "code");
        this.title = Objects.requireNonNull(title, "title");
    }

    public ErrorKind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }
}
