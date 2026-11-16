package io.github.asmeduardo.secureledger.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.ledger.api.StatementLine;
import io.github.asmeduardo.secureledger.ledger.api.TransactionKind;
import io.github.asmeduardo.secureledger.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The export reads the period in short pages, oldest first, up to the entry that was last at the start. */
class StatementExportTest {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-01T00:00:00Z");

    private final Ledger ledger = mock(Ledger.class);

    @Test
    void pagesThroughThePeriodWithoutRepeatingOrSkippingEntries() {
        when(ledger.statementBetween(ACCOUNT, FROM, TO, 0, 90, 2)).thenReturn(List.of(line(11), line(12)));
        when(ledger.statementBetween(ACCOUNT, FROM, TO, 12, 90, 2)).thenReturn(List.of(line(40), line(41)));
        when(ledger.statementBetween(ACCOUNT, FROM, TO, 41, 90, 2)).thenReturn(List.of(line(90)));

        var visited = new ArrayList<Long>();
        new StatementExport(ledger, ACCOUNT, FROM, TO, 90, 2).forEach(view -> visited.add(view.entryId()));

        assertThat(visited).containsExactly(11L, 12L, 40L, 41L, 90L);
    }

    @Test
    void stopsAfterAnEmptyPage() {
        when(ledger.statementBetween(ACCOUNT, FROM, TO, 0, 12, 2)).thenReturn(List.of(line(11), line(12)));
        when(ledger.statementBetween(ACCOUNT, FROM, TO, 12, 12, 2)).thenReturn(List.of());

        var visited = new ArrayList<Long>();
        new StatementExport(ledger, ACCOUNT, FROM, TO, 12, 2).forEach(view -> visited.add(view.entryId()));

        assertThat(visited).containsExactly(11L, 12L);
        verify(ledger).statementBetween(ACCOUNT, FROM, TO, 0, 12, 2);
        verify(ledger).statementBetween(ACCOUNT, FROM, TO, 12, 12, 2);
        verifyNoMoreInteractions(ledger);
    }

    private static StatementLine line(long entryId) {
        return new StatementLine(
                entryId,
                UUID.randomUUID(),
                TransactionKind.DEPOSIT,
                Money.of("1.00", "BRL"),
                Money.of("1.00", "BRL"),
                null,
                FROM.plusSeconds(entryId));
    }
}
