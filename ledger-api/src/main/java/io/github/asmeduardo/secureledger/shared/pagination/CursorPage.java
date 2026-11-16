package io.github.asmeduardo.secureledger.shared.pagination;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * One page of a newest-first list. Items written after the first page was read never shift later
 * pages: each page continues strictly below the last id already returned.
 *
 * @param nextCursor pass it back to read the next page; {@code null} on the last page
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    /** Largest page the API serves. */
    public static final int MAX_LIMIT = 100;

    public CursorPage {
        items = List.copyOf(items);
    }

    /**
     * Rows to read for a page of {@code limit} items: one more, which only tells whether another page
     * exists. The limit is checked here too, so a caller that skipped the HTTP validation cannot ask
     * for an unbounded page.
     */
    public static int rowsToRead(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("A page holds between 1 and " + MAX_LIMIT + " items");
        }
        return limit + 1;
    }

    /**
     * Builds a page from up to {@code limit + 1} rows read in descending id order; the extra row only
     * tells whether there is a next page.
     */
    public static <T> CursorPage<T> of(List<T> rows, int limit, ToLongFunction<T> idOf) {
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }
        var page = rows.subList(0, limit);
        return new CursorPage<>(page, Cursor.encode(idOf.applyAsLong(page.getLast())));
    }
}
