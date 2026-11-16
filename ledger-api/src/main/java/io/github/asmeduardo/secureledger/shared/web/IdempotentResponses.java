package io.github.asmeduardo.secureledger.shared.web;

import io.github.asmeduardo.secureledger.shared.idempotency.IdempotentResult;
import java.net.URI;
import org.springframework.http.ResponseEntity;

/** HTTP side of idempotent writes: the request header and how a replay is answered. */
public final class IdempotentResponses {

    public static final String KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final String KEY_PATTERN = "[A-Za-z0-9._:-]{8,100}";
    public static final String KEY_MESSAGE = "must be 8-100 characters: letters, digits or . _ : -";
    public static final String KEY_DESCRIPTION =
            "Client-generated key, unique per operation attempt for the calling client (a UUID is a good choice). "
                    + "Retrying with the same key and body returns the original response.";

    private IdempotentResponses() {}

    /** 201 with the location of the resource; a replay also carries {@code Idempotent-Replayed: true}. */
    public static <T> ResponseEntity<T> created(URI location, IdempotentResult<T> result) {
        var response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(result.response());
    }
}
