package io.github.asmeduardo.secureledger.audit.application;

import io.github.asmeduardo.secureledger.audit.api.AuditRecord;
import io.github.asmeduardo.secureledger.audit.api.AuditTrail;
import io.github.asmeduardo.secureledger.shared.pagination.Cursor;
import io.github.asmeduardo.secureledger.shared.pagination.CursorPage;
import io.github.asmeduardo.secureledger.shared.security.ClientId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService implements AuditTrail {

    private final AuditEventStore events;
    private final Clock clock;

    AuditService(AuditEventStore events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(AuditRecord record) {
        events.append(record, clock.instant());
    }

    /** Events of requests made by the client itself, newest first. */
    @Transactional(readOnly = true)
    public CursorPage<AuditEventView> ownEvents(ClientId client, String cursor, int limit) {
        return page(client.value(), cursor, limit);
    }

    /** Back office view: events of every client, or of one actor when given. */
    @Transactional(readOnly = true)
    public CursorPage<AuditEventView> allEvents(String actor, String cursor, int limit) {
        return page(actor, cursor, limit);
    }

    private CursorPage<AuditEventView> page(String actor, String cursor, int limit) {
        var rows = events.page(actor, Cursor.decode(cursor), CursorPage.rowsToRead(limit));
        return CursorPage.of(rows, limit, AuditEventView::id);
    }
}
