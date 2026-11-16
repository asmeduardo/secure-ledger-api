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

    public CursorPage {
        items = List.copyOf(items);
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
