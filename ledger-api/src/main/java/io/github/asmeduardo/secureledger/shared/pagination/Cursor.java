package io.github.asmeduardo.secureledger.shared.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque position in an append-only, newest-first list: the id of the last item a page returned.
 * Clients pass it back unchanged; its format may change without breaking them.
 */
public final class Cursor {

    private static final String VERSION = "v1:";

    private Cursor() {}

    public static String encode(long lastId) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((VERSION + lastId).getBytes(StandardCharsets.US_ASCII));
    }

    /** The id after which the next page starts, or {@link Long#MAX_VALUE} for the first page. */
    public static long decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return Long.MAX_VALUE;
        }
        try {
            var text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!text.startsWith(VERSION)) {
                throw new InvalidCursorException();
            }
            var id = Long.parseLong(text.substring(VERSION.length()));
            if (id <= 0) {
                throw new InvalidCursorException();
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException();
        }
    }
}
