package io.github.asmeduardo.secureledger.accounts.web;

import io.github.asmeduardo.secureledger.accounts.application.StatementLineView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes statement lines as RFC 4180 CSV. A text cell that a spreadsheet would run as a formula
 * (starting with = + - @ or a control character) is prefixed with an apostrophe, so an exported
 * reference can never execute anything on the machine of whoever opens the file.
 */
final class StatementCsv {

    static final String HEADER =
            "entry_id,posted_at,transaction_id,type,direction,amount,currency,balance_after,reference";
    private static final String LINE_END = "\r\n";

    private final Writer out;

    StatementCsv(Writer out) {
        this.out = out;
    }

    void header() {
        write(HEADER);
    }

    void line(StatementLineView line) {
        write(Stream.of(
                        String.valueOf(line.entryId()),
                        line.postedAt().toString(),
                        line.transactionId().toString(),
                        line.type(),
                        line.direction(),
                        line.amount().toPlainString(),
                        line.currency(),
                        line.balanceAfter().toPlainString(),
                        text(line.reference()))
                .collect(Collectors.joining(",")));
    }

    static String text(String value) {
        if (value == null) {
            return "";
        }
        var safe = value.isEmpty() || "=+-@\t\r".indexOf(value.charAt(0)) < 0 ? value : "'" + value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private void write(String record) {
        try {
            out.write(record);
            out.write(LINE_END);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
