package io.github.asmeduardo.secureledger.transfers;

import static io.github.asmeduardo.secureledger.support.Responses.body;
import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class ReversalApiIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("reversal-wallet");

    @Autowired
    MockMvcTester mvc;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void reversalPostsTheOppositeEntriesAndKeepsTheOriginalUntouched() {
        var account = api.openAccount(WALLET, "BRL");
        var deposit = api.deposit(WALLET, account, "40.00", "BRL");
        String depositId = read(deposit, "$.id");

        var reversal = reverse(WALLET, LedgerApi.newKey(), depositId);

        assertThat(reversal).hasStatus(HttpStatus.CREATED);
        assertThat(reversal).bodyJson().isLenientlyEqualTo("""
                {"type": "REVERSAL", "amount": "40.00", "currency": "BRL", "reference": "deposited twice",
                 "reversesTransactionId": "%s",
                 "entries": [
                   {"accountId": "00000000-0000-4000-8000-000000000986", "direction": "CREDIT", "amount": "40.00"},
                   {"accountId": "%s", "direction": "DEBIT", "amount": "40.00", "balanceAfter": "0.00"}
                 ]}
                """.formatted(depositId, account));
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
        assertThat(body(mvc.get()
                        .uri("/api/v1/transactions/{id}", depositId)
                        .with(WALLET)
                        .exchange()))
                .isEqualTo(body(deposit));
    }

    @Test
    void aTransactionIsReversedOnlyOnce() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "10.00", "BRL"), "$.id");
        reverse(WALLET, LedgerApi.newKey(), depositId);

        assertProblem(reverse(WALLET, LedgerApi.newKey(), depositId), HttpStatus.CONFLICT, "already-reversed");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentReversalsOfOneTransactionPostASingleReversal() throws Exception {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "10.00", "BRL"), "$.id");

        var responses = new ArrayList<MvcTestResult>();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<MvcTestResult>>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return reverse(WALLET, LedgerApi.newKey(), depositId);
                }));
            }
            start.countDown();
            for (var future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
        }

        assertThat(statuses(responses)).containsOnlyOnce(201).containsOnly(201, 409);
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
    }

    @Test
    void aReversalCannotItselfBeReversed() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "10.00", "BRL"), "$.id");
        String reversalId = read(reverse(WALLET, LedgerApi.newKey(), depositId), "$.id");

        assertProblem(
                reverse(WALLET, LedgerApi.newKey(), reversalId),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "reversal-not-allowed");
    }

    @Test
    void aReversalThatWouldOverdrawAnAccountIsRefused() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "40.00", "BRL"), "$.id");
        api.withdraw(WALLET, account, "30.00", "BRL");

        assertProblem(
                reverse(WALLET, LedgerApi.newKey(), depositId), HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("10.00");
    }

    @Test
    void onlyTheOwnerOfTheAccountsMayReverse() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "10.00", "BRL"), "$.id");

        assertProblem(
                reverse(Tokens.operator("reversal-intruder"), LedgerApi.newKey(), depositId),
                HttpStatus.NOT_FOUND,
                "transaction-not-found");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("10.00");
    }

    @Test
    void retryingAReversalReplaysIt() {
        var account = api.openAccount(WALLET, "BRL");
        String depositId = read(api.deposit(WALLET, account, "10.00", "BRL"), "$.id");
        var key = LedgerApi.newKey();

        var first = reverse(WALLET, key, depositId);
        var retry = reverse(WALLET, key, depositId);

        assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(body(retry)).isEqualTo(body(first));
    }

    private MvcTestResult reverse(JwtRequestPostProcessor client, String key, String transactionId) {
        return api.post(client, key, "/api/v1/transactions/" + transactionId + "/reversal", """
                {"reference": "deposited twice"}
                """);
    }

    private static List<Integer> statuses(List<MvcTestResult> responses) {
        return responses.stream().map(r -> r.getResponse().getStatus()).toList();
    }

    private static void assertProblem(MvcTestResult response, HttpStatus status, String code) {
        assertThat(response)
                .hasStatus(status)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:" + code);
    }
}
