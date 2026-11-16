package io.github.asmeduardo.secureledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Requests racing for the same balance. Each scenario starts all requests at once and then checks
 * the outcome against the ledger itself: the materialized balance must equal the sum of the entries.
 */
@IntegrationTest
class ConcurrencyIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("concurrency-wallet");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void simultaneousDebitsNeverSpendTheSameBalanceTwice() throws Exception {
        var account = api.fundedAccount(WALLET, "BRL", "10.00");
        int requests = 40;

        var responses = runAtOnce(requests, () -> api.withdraw(WALLET, account, "1.00", "BRL"));

        assertThat(responses)
                .filteredOn(r -> r.getResponse().getStatus() == 201)
                .hasSize(10);
        assertThat(responses)
                .filteredOn(r -> r.getResponse().getStatus() == 422)
                .hasSize(30)
                .allSatisfy(r -> assertThat(r)
                        .bodyJson()
                        .extractingPath("$.type")
                        .isEqualTo("urn:secure-ledger:problem:insufficient-funds"));
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
        assertBalanceMatchesEntries(account);
        assertThat(jdbc.queryForObject(
                        "select min(balance_after) from ledger.entry where account_id = ?", BigDecimal.class, account))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void crossedTransfersBetweenTwoAccountsDoNotDeadlock() throws Exception {
        var first = api.fundedAccount(WALLET, "BRL", "1000.00");
        var second = api.fundedAccount(WALLET, "BRL", "1000.00");
        int perDirection = 20;

        var responses = runAtOnce(
                perDirection * 2,
                index -> index % 2 == 0
                        ? api.transfer(WALLET, first, second, "1.00", "BRL")
                        : api.transfer(WALLET, second, first, "1.00", "BRL"));

        assertThat(responses).allSatisfy(response -> assertThat(response).hasStatus(HttpStatus.CREATED));
        assertThat(api.balance(WALLET, first)).isEqualByComparingTo("1000.00");
        assertThat(api.balance(WALLET, second)).isEqualByComparingTo("1000.00");
        assertBalanceMatchesEntries(first);
        assertBalanceMatchesEntries(second);
    }

    @Test
    void depositsAndWithdrawalsOfManyAccountsDoNotWaitForEachOtherOnSettlement() throws Exception {
        var accounts = new ArrayList<UUID>();
        for (int i = 0; i < 8; i++) {
            accounts.add(api.fundedAccount(WALLET, "USD", "5.00"));
        }

        var responses = runAtOnce(
                accounts.size() * 2,
                index -> index % 2 == 0
                        ? api.deposit(WALLET, accounts.get(index / 2), "2.00", "USD")
                        : api.withdraw(WALLET, accounts.get(index / 2), "3.00", "USD"));

        assertThat(responses).allSatisfy(response -> assertThat(response).hasStatus(HttpStatus.CREATED));
        for (var account : accounts) {
            assertThat(api.balance(WALLET, account)).isEqualByComparingTo("4.00");
            assertBalanceMatchesEntries(account);
        }
    }

    private void assertBalanceMatchesEntries(UUID account) {
        var stored = jdbc.queryForObject(
                "select balance from ledger.ledger_account where id = ?", BigDecimal.class, account);
        var sum = jdbc.queryForObject(
                "select coalesce(sum(amount), 0) from ledger.entry where account_id = ?", BigDecimal.class, account);
        assertThat(stored).as("materialized balance of %s", account).isEqualByComparingTo(sum);
    }

    private static List<MvcTestResult> runAtOnce(int count, Callable<MvcTestResult> request) throws Exception {
        return runAtOnce(count, index -> request.call());
    }

    private static List<MvcTestResult> runAtOnce(int count, IndexedRequest request) throws Exception {
        var start = new CountDownLatch(1);
        var results = new ArrayList<MvcTestResult>();
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<MvcTestResult>>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call(index);
                }));
            }
            start.countDown();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
        }
        return results;
    }

    @FunctionalInterface
    private interface IndexedRequest {
        MvcTestResult call(int index) throws Exception;
    }
}
