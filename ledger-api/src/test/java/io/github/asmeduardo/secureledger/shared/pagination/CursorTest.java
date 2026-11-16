package io.github.asmeduardo.secureledger.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorTest {

    @Test
    void decodesWhatItEncodes() {
        assertThat(Cursor.decode(Cursor.encode(42))).isEqualTo(42);
        assertThat(Cursor.encode(42)).doesNotContain("42").matches("[A-Za-z0-9_-]+");
    }

    @Test
    void withoutACursorTheFirstPageStartsAtTheNewestItem() {
        assertThat(Cursor.decode(null)).isEqualTo(Long.MAX_VALUE);
        assertThat(Cursor.decode("")).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-cursor", "djE6YWJj", "djI6NDI", "djE6LTE", "%%%"})
    void refusesAnythingItDidNotIssue(String cursor) {
        assertThatThrownBy(() -> Cursor.decode(cursor)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void aPageOffersANextCursorOnlyWhenMoreRowsExist() {
        var full = CursorPage.of(List.of(9L, 8L, 7L), 2, Function.<Long>identity()::apply);
        var last = CursorPage.of(List.of(6L), 2, Function.<Long>identity()::apply);

        assertThat(full.items()).containsExactly(9L, 8L);
        assertThat(Cursor.decode(full.nextCursor())).isEqualTo(8L);
        assertThat(last.items()).containsExactly(6L);
        assertThat(last.nextCursor()).isNull();
    }
}
