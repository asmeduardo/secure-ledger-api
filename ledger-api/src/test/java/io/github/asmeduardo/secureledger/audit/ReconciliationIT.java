package io.github.asmeduardo.secureledger.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.audit.application.ReconciliationJob;
import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reconciliation compares every materialized balance with the sum of the entries and looks for
 * transactions that do not balance. Divergences are injected straight into the database, the
 * unbalanced transaction even with the triggers switched off, as a faulty script or a manual fix
 * could do. Each test restores the ledger afterwards, because the database is shared.
 */
@IntegrationTest
class ReconciliationIT {

    private static final JwtRequestPostProcessor AUDITOR = Tokens.client("reconciliation-auditor", Tokens.AUDIT);
    private static final JwtRequestPostProcessor WALLET = Tokens.operator("reconciliation-wallet");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ReconciliationJob job;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void aLedgerWrittenOnlyThroughTheApplicationIsConsistent() {
        var source = api.fundedAccount(WALLET, "BRL", "30.00");
        api.transfer(WALLET, source, api.openAccount(WALLET, "BRL"), "12.00", "BRL");

        var report = reconcile();

        assertThat(report).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"consistent": true, "balanceDivergences": [], "unbalancedTransactions": []}
                """);
        assertThat(report)
                .bodyJson()
                .extractingPath("$.accountsChecked")
                .asNumber()
                .isNotEqualTo(0);
        assertThat(report).bodyJson().hasPath("$.checkedAt");
    }

    @Test
    void reportsAMaterializedBalanceThatNoLongerMatchesItsEntries() {
        var account = api.fundedAccount(WALLET, "BRL", "10.00");
        jdbc.update("update ledger.ledger_account set balance = 15.00 where id = ?", account);
        try {
            var report = reconcile();

            assertThat(report).bodyJson().extractingPath("$.consistent").isEqualTo(false);
            assertThat(report).bodyJson().isLenientlyEqualTo("""
                    {"balanceDivergences": [{"accountId": "%s", "currency": "BRL",
                                             "materializedBalance": "15.00", "ledgerBalance": "10.00"}]}
                    """.formatted(account));
        } finally {
            jdbc.update("update ledger.ledger_account set balance = 10.00 where id = ?", account);
        }
        assertThat(reconcile()).bodyJson().extractingPath("$.consistent").isEqualTo(true);
    }

    @Test
    void reportsATransactionWrittenPastTheDatabaseGuards() {
        var account = api.fundedAccount(WALLET, "BRL", "10.00");
        var forged = UUID.randomUUID();
        bypassingTriggers(() -> {
            var now = Timestamp.from(Instant.now());
            jdbc.update(
                    "insert into ledger.ledger_transaction (id, type, currency, initiated_by, created_at) values (?, 'DEPOSIT', 'BRL', 'manual-fix', ?)",
                    forged,
                    now);
            jdbc.update(
                    "insert into ledger.entry (transaction_id, account_id, currency, amount, balance_after, created_at) values (?, ?, 'BRL', 5.00, 15.00, ?)",
                    forged,
                    account,
                    now);
            jdbc.update("update ledger.ledger_account set balance = 15.00 where id = ?", account);
        });
        try {
            var report = reconcile();

            assertThat(report).bodyJson().extractingPath("$.consistent").isEqualTo(false);
            assertThat(report).bodyJson().isLenientlyEqualTo("""
                    {"balanceDivergences": [],
                     "unbalancedTransactions": [{"transactionId": "%s", "entryCount": 1, "entrySum": "5.00"}]}
                    """.formatted(forged));
        } finally {
            bypassingTriggers(() -> {
                jdbc.update("delete from ledger.entry where transaction_id = ?", forged);
                jdbc.update("delete from ledger.ledger_transaction where id = ?", forged);
                jdbc.update("update ledger.ledger_account set balance = 10.00 where id = ?", account);
            });
        }
    }

    @Test
    void everyRunLeavesAnAuditEvent() {
        reconcile();
        job.run();

        assertThat(mvc.get()
                        .uri("/api/v1/admin/audit-events?actor=reconciliation-auditor&limit=1")
                        .with(AUDITOR))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"items": [{"operation": "RECONCILIATION", "outcome": "SUCCEEDED",
                                    "request": {"balanceDivergences": "0", "unbalancedTransactions": "0"}}]}
                        """);
        assertThat(mvc.get()
                        .uri("/api/v1/admin/audit-events?actor=system:reconciliation&limit=1")
                        .with(AUDITOR))
                .bodyJson()
                .extractingPath("$.items[0].operation")
                .isEqualTo("RECONCILIATION");
    }

    @Test
    void onlyTheBackOfficeMayReconcile() {
        assertThat(mvc.post().uri("/api/v1/admin/reconciliations").with(WALLET)).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult reconcile() {
        return mvc.post().uri("/api/v1/admin/reconciliations").with(AUDITOR).exchange();
    }

    /** The test database user is a superuser, which may run with triggers and foreign keys disabled. */
    private void bypassingTriggers(Runnable writes) {
        transactions.executeWithoutResult(status -> {
            jdbc.execute("set local session_replication_role = replica");
            writes.run();
        });
    }
}
