package io.github.asmeduardo.secureledger.accounts;

import static io.github.asmeduardo.secureledger.support.Responses.body;
import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class StatementExportIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("export-wallet");
    private static final String HEADER =
            "entry_id,posted_at,transaction_id,type,direction,amount,currency,balance_after,reference";

    @Autowired
    MockMvcTester mvc;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void exportsTheEntriesOfThePeriodOldestFirstAsCsv() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(depositWithReference(account, "50.00", "salario, novembro"), "$.id");
        String withdrawalId = read(api.withdraw(WALLET, account, "20.00", "BRL"), "$.id");

        var export = export(
                account, Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));

        assertThat(export).hasStatusOk();
        assertThat(export.getResponse().getContentType()).startsWith("text/csv");
        assertThat(export.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment; filename=\"statement-" + account);
        var lines = body(export).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1]).contains("," + depositId + ",DEPOSIT,CREDIT,50.00,BRL,50.00,\"salario, novembro\"");
        assertThat(lines[2]).contains("," + withdrawalId + ",WITHDRAWAL,DEBIT,20.00,BRL,30.00,");
    }

    @Test
    void aReferenceThatLooksLikeASpreadsheetFormulaIsNeutralized() {
        var account = api.openAccount(WALLET, "BRL");
        depositWithReference(account, "1.00", "=HYPERLINK(\"http://evil.example\")");

        var export = export(
                account, Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));

        assertThat(body(export)).contains(",\"'=HYPERLINK(\"\"http://evil.example\"\")\"\r\n");
    }

    @Test
    void entriesOutsideThePeriodAreLeftOut() {
        var account = api.fundedAccount(WALLET, "BRL", "5.00");

        var later = export(
                account, Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(2)));

        assertThat(body(later)).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void thePeriodIsValidated() {
        var account = api.openAccount(WALLET, "BRL");
        var now = Instant.now();

        assertProblem(export(account, now, now), HttpStatus.BAD_REQUEST, "invalid-period");
        assertProblem(export(account, now.minus(Duration.ofDays(367)), now), HttpStatus.BAD_REQUEST, "invalid-period");
        assertThat(mvc.get()
                        .uri("/api/v1/accounts/{id}/statement.csv?to={to}", account, now)
                        .with(WALLET))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("from");
    }

    @Test
    void theStatementOfAnotherClientIsNotFound() {
        var foreign = api.fundedAccount(Tokens.operator("export-owner"), "BRL", "5.00");

        assertProblem(
                export(foreign, Instant.now().minus(Duration.ofHours(1)), Instant.now()),
                HttpStatus.NOT_FOUND,
                "account-not-found");
    }

    private MvcTestResult depositWithReference(UUID account, String amount, String reference) {
        var escaped = reference.replace("\"", "\\\"");
        var response =
                api.post(WALLET, LedgerApi.newKey(), "/api/v1/deposits", """
                {"accountId": "%s", "amount": "%s", "currency": "BRL", "reference": "%s"}
                """.formatted(account, amount, escaped));
        assertThat(response).hasStatus(HttpStatus.CREATED);
        return response;
    }

    private MvcTestResult export(UUID account, Instant from, Instant to) {
        return mvc.get()
                .uri("/api/v1/accounts/{id}/statement.csv?from={from}&to={to}", account, from, to)
                .with(WALLET)
                .exchange();
    }

    private static void assertProblem(MvcTestResult response, HttpStatus status, String code) {
        assertThat(response)
                .hasStatus(status)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:" + code);
    }
}
