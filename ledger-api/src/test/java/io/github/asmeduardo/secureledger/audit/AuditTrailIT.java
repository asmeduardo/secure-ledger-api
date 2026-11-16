package io.github.asmeduardo.secureledger.audit;

import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AuditTrailIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    LedgerApi api;
    String clientId;
    JwtRequestPostProcessor client;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
        clientId = "audited-" + UUID.randomUUID();
        client = Tokens.operator(clientId);
    }

    @Test
    void aSuccessfulMovementRecordsWhoDidWhatWhenAndTheOutcome() {
        var source = api.fundedAccount(client, "BRL", "50.00");
        var destination = api.openAccount(client, "BRL");
        var key = LedgerApi.newKey();
        String transactionId = read(api.transfer(client, key, source, destination, "20.00", "BRL"), "$.id");

        var events = ownEvents(client, 1);

        assertThat(events).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"items": [{
                  "actor": "%s", "operation": "TRANSFER", "outcome": "SUCCEEDED",
                  "resourceId": "%s", "idempotencyKey": "%s",
                  "request": {"sourceAccountId": "%s", "destinationAccountId": "%s",
                              "amount": "20.00", "currency": "BRL"}
                }]}
                """.formatted(
                        clientId, transactionId, key, source, destination));
        assertThat(events).bodyJson().hasPath("$.items[0].occurredAt");
    }

    @Test
    void aRetryIsRecordedAsAReplayOfTheSameTransaction() {
        var account = api.openAccount(client, "BRL");
        var key = LedgerApi.newKey();
        String transactionId = read(api.deposit(client, key, account, "7.00", "BRL"), "$.id");
        api.deposit(client, key, account, "7.00", "BRL");

        var events = ownEvents(client, 2);

        assertThat(events)
                .bodyJson()
                .extractingPath("$.items[*].outcome")
                .asArray()
                .containsExactly("REPLAYED", "SUCCEEDED");
        assertThat(events)
                .bodyJson()
                .extractingPath("$.items[*].resourceId")
                .asArray()
                .containsOnly(transactionId);
    }

    @Test
    void aRefusedRequestIsRecordedWithTheRuleThatRefusedIt() {
        var account = api.fundedAccount(client, "BRL", "5.00");
        var foreign = api.openAccount(Tokens.operator("audit-other-client"), "BRL");
        api.withdraw(client, account, "9.00", "BRL");
        api.withdraw(client, foreign, "1.00", "BRL");

        var events = ownEvents(client, 2);

        assertThat(events)
                .bodyJson()
                .extractingPath("$.items[*].reason")
                .asArray()
                .containsExactly("account-not-found", "insufficient-funds");
        assertThat(events)
                .bodyJson()
                .extractingPath("$.items[*].outcome")
                .asArray()
                .containsOnly("REJECTED");
        assertThat(events).bodyJson().extractingPath("$.items[0].resourceId").isNull();
    }

    @Test
    void openingAnAccountIsAuditedToo() {
        var account = api.openAccount(client, "EUR");

        assertThat(ownEvents(client, 1)).bodyJson().isLenientlyEqualTo("""
                {"items": [{"operation": "OPEN_ACCOUNT", "outcome": "SUCCEEDED", "resourceId": "%s",
                            "request": {"currency": "EUR"}}]}
                """.formatted(account));
    }

    @Test
    void aClientSeesOnlyItsOwnEvents() {
        api.openAccount(client, "BRL");

        var otherEvents = ownEvents(Tokens.operator("audit-curious-" + UUID.randomUUID()), 50);

        assertThat(otherEvents)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items")
                .asArray()
                .isEmpty();
    }

    @Test
    void eventsArePagedNewestFirstWithAStableCursor() {
        var first = api.openAccount(client, "BRL");
        var second = api.openAccount(client, "BRL");
        var third = api.openAccount(client, "BRL");

        var page1 = ownEvents(client, 2);
        String cursor = read(page1, "$.nextCursor");
        api.openAccount(client, "BRL");
        var page2 = mvc.get()
                .uri("/api/v1/audit-events?limit=2&cursor={cursor}", cursor)
                .with(client)
                .exchange();

        assertThat(page1)
                .bodyJson()
                .extractingPath("$.items[*].resourceId")
                .asArray()
                .containsExactly(third.toString(), second.toString());
        assertThat(page2)
                .bodyJson()
                .extractingPath("$.items[*].resourceId")
                .asArray()
                .containsExactly(first.toString());
        assertThat(page2).bodyJson().extractingPath("$.nextCursor").isNull();
    }

    @Test
    void aMalformedCursorIsABadRequest() {
        assertThat(mvc.get().uri("/api/v1/audit-events?cursor=not-a-cursor").with(client))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:invalid-cursor");
    }

    @Test
    void auditorsReadTheEventsOfAnyClient() {
        var account = api.openAccount(client, "BRL");

        var response = mvc.get()
                .uri("/api/v1/admin/audit-events?actor={actor}&limit=5", clientId)
                .with(Tokens.client("back-office", Tokens.AUDIT))
                .exchange();

        assertThat(response)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.items[0].resourceId")
                .isEqualTo(account.toString());
        assertThat(mvc.get().uri("/api/v1/admin/audit-events").with(client)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theTrailIsAppendOnly() {
        api.openAccount(client, "BRL");

        assertThatThrownBy(() ->
                        jdbc.update("update audit.audit_event set outcome = 'REJECTED' where actor = ?", clientId))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit.audit_event where actor = ?", clientId))
                .rootCause()
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate audit.audit_event"))
                .rootCause()
                .hasMessageContaining("append-only");
    }

    private MvcTestResult ownEvents(JwtRequestPostProcessor token, int limit) {
        return mvc.get()
                .uri("/api/v1/audit-events?limit={limit}", limit)
                .with(token)
                .exchange();
    }
}
