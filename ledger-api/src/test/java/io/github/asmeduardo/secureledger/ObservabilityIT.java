package io.github.asmeduardo.secureledger;

import static io.github.asmeduardo.secureledger.support.Responses.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Health and metrics are scraped without a token; nothing else of the actuator is exposed. */
@IntegrationTest
class ObservabilityIT {

    @Autowired
    WebApplicationContext context;

    @Autowired
    MockMvcTester authenticated;

    MockMvcTester anonymous;

    @BeforeEach
    void buildMockMvcWithoutToken() {
        anonymous = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build());
    }

    @Test
    void healthAndReadinessArePublic() {
        assertThat(anonymous.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
        assertThat(anonymous.get().uri("/actuator/health/readiness")).hasStatusOk();
    }

    @Test
    void prometheusCountsOperationsByOutcome() {
        var api = new LedgerApi(authenticated);
        var wallet = Tokens.operator("metrics-wallet");
        var account = api.fundedAccount(wallet, "BRL", "1.00");
        api.withdraw(wallet, account, "5.00", "BRL");

        var scrape = anonymous.get().uri("/actuator/prometheus").exchange();

        assertThat(scrape).hasStatusOk();
        assertThat(body(scrape))
                .containsPattern("ledger_operations_total\\{[^}]*operation=\"DEPOSIT\"[^}]*outcome=\"SUCCEEDED\"")
                .containsPattern("ledger_operations_total\\{[^}]*operation=\"WITHDRAWAL\"[^}]*outcome=\"REJECTED\"")
                .contains("http_server_requests_seconds");
    }

    @Test
    void prometheusShowsTheDivergencesFoundByTheLastReconciliation() {
        authenticated
                .post()
                .uri("/api/v1/admin/reconciliations")
                .with(Tokens.client("metrics-auditor", Tokens.AUDIT))
                .exchange();

        assertThat(body(anonymous.get().uri("/actuator/prometheus").exchange()))
                .contains("ledger_reconciliation_divergences{application=\"ledger-api\"} 0.0");
    }

    @Test
    void otherActuatorEndpointsStayClosed() {
        assertThat(anonymous.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.get().uri("/actuator/beans")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
