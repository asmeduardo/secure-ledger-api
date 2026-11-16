package io.github.asmeduardo.secureledger.shared.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details whose {@code type} is a stable, documented identifier. */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:secure-ledger:problem:";
    public static final String MEDIA_TYPE = "application/problem+json";

    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String code, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        return problem;
    }
}
