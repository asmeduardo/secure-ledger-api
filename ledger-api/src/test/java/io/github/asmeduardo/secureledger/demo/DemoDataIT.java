package io.github.asmeduardo.secureledger.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.Tokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The demo profile seeds fictitious accounts once, and the seeded ledger reconciles. */
@IntegrationTest
@ActiveProfiles("demo")
class DemoDataIT {

    private static final String ANA = "a1000000-0000-4000-8000-000000000001";
    private static final String BRUNO = "a1000000-0000-4000-8000-000000000002";
    private static final String MARKETPLACE = "b2000000-0000-4000-8000-000000000001";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationRunner demoData;

    @Test
    void theDemoWalletOwnsTwoFundedAccounts() {
        assertThat(mvc.get().uri("/api/v1/accounts").with(Tokens.operator("demo-wallet")))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"totalElements": 2, "content": [
                          {"id": "%s", "currency": "BRL", "balance": "1000.00"},
                          {"id": "%s", "currency": "BRL", "balance": "250.00"}
                        ]}
                        """.formatted(ANA, BRUNO));
        assertThat(mvc.get().uri("/api/v1/accounts/{id}", MARKETPLACE).with(Tokens.operator("demo-marketplace")))
                .bodyJson()
                .extractingPath("$.balance")
                .isEqualTo("5000.00");
    }

    @Test
    void theDemoClientsCannotSeeEachOtherAccounts() {
        assertThat(mvc.get().uri("/api/v1/accounts/{id}", MARKETPLACE).with(Tokens.operator("demo-wallet")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theInitialBalancesComeFromAuditedDeposits() {
        assertThat(mvc.get().uri("/api/v1/accounts/{id}/entries", ANA).with(Tokens.operator("demo-wallet")))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"items": [{"type": "DEPOSIT", "direction": "CREDIT", "amount": "1000.00",
                                    "balanceAfter": "1000.00"}]}
                        """);
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit.audit_event where actor = 'system:demo-seed' and outcome = 'SUCCEEDED'",
                        Integer.class))
                .isEqualTo(6);
    }

    @Test
    void theSeededLedgerReconciles() {
        assertThat(mvc.post().uri("/api/v1/admin/reconciliations").with(Tokens.client("demo-auditor", Tokens.AUDIT)))
                .bodyJson()
                .extractingPath("$.consistent")
                .isEqualTo(true);
    }

    @Test
    void seedingAgainChangesNothing() throws Exception {
        var transactions = jdbc.queryForObject("select count(*) from ledger.ledger_transaction", Long.class);

        demoData.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from ledger.ledger_transaction", Long.class))
                .isEqualTo(transactions);
    }
}
