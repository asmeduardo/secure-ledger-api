package io.github.asmeduardo.secureledger.transfers;

import static io.github.asmeduardo.secureledger.support.Responses.body;
import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.ArrayList;
import java.util.UUID;
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

@IntegrationTest
class IdempotencyIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("idempotency-wallet");

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
    void retryWithTheSameKeyAndPayloadReturnsTheSameResponseWithoutMovingMoneyAgain() {
        var source = api.fundedAccount(WALLET, "BRL", "100.00");
        var destination = api.openAccount(WALLET, "BRL");
        var key = LedgerApi.newKey();

        var first = api.transfer(WALLET, key, source, destination, "40.00", "BRL");
        var retry = api.transfer(WALLET, key, source, destination, "40", "BRL");

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(retry.getResponse().getHeader("Location"))
                .isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(api.balance(WALLET, source)).isEqualByComparingTo("60.00");
        assertThat(entriesOf(destination)).isOne();
    }

    @Test
    void reusingAKeyForAnotherPayloadIsRejected() {
        var account = api.openAccount(WALLET, "BRL");
        var key = LedgerApi.newKey();
        api.deposit(WALLET, key, account, "10.00", "BRL");

        var reuse = api.deposit(WALLET, key, account, "11.00", "BRL");

        assertThat(reuse)
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:idempotency-key-reused");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("10.00");
    }

    @Test
    void reusingAKeyForAnotherOperationIsRejected() {
        var account = api.fundedAccount(WALLET, "BRL", "50.00");
        var key = LedgerApi.newKey();
        api.deposit(WALLET, key, account, "10.00", "BRL");

        assertThat(api.withdraw(WALLET, key, account, "10.00", "BRL")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("60.00");
    }

    @Test
    void keysOfDifferentClientsNeverCollide() {
        var walletA = Tokens.operator("tenant-a-" + UUID.randomUUID());
        var walletB = Tokens.operator("tenant-b-" + UUID.randomUUID());
        var accountA = api.openAccount(walletA, "BRL");
        var accountB = api.openAccount(walletB, "BRL");
        var sharedKey = "deposit-0001";

        var fromA = api.deposit(walletA, sharedKey, accountA, "5.00", "BRL");
        var fromB = api.deposit(walletB, sharedKey, accountB, "5.00", "BRL");

        assertThat(fromA).hasStatus(HttpStatus.CREATED);
        assertThat(fromB).hasStatus(HttpStatus.CREATED);
        assertThat(fromB.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(read(fromB, "$.id").toString()).isNotEqualTo(read(fromA, "$.id"));
        assertThat(api.balance(walletB, accountB)).isEqualByComparingTo("5.00");
    }

    @Test
    void concurrentRequestsWithTheSameKeyProduceASingleMovement() throws Exception {
        var account = api.fundedAccount(WALLET, "BRL", "100.00");
        var key = LedgerApi.newKey();
        int clients = 12;
        var start = new CountDownLatch(1);

        var ids = new ArrayList<String>();
        try (var executor = Executors.newFixedThreadPool(clients)) {
            var futures = new ArrayList<Future<MvcTestResult>>();
            for (int i = 0; i < clients; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return api.withdraw(WALLET, key, account, "30.00", "BRL");
                }));
            }
            start.countDown();
            for (var future : futures) {
                var result = future.get(60, TimeUnit.SECONDS);
                assertThat(result).hasStatus(HttpStatus.CREATED);
                ids.add(read(result, "$.id"));
            }
        }

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("70.00");
        assertThat(entriesOf(account)).isEqualTo(2);
    }

    @Test
    void aRejectedRequestDoesNotUseUpItsKey() {
        var account = api.fundedAccount(WALLET, "BRL", "5.00");
        var key = LedgerApi.newKey();

        assertThat(api.withdraw(WALLET, key, account, "8.00", "BRL")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        api.deposit(WALLET, account, "5.00", "BRL");
        var retry = api.withdraw(WALLET, key, account, "8.00", "BRL");

        assertThat(retry).hasStatus(HttpStatus.CREATED);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("2.00");
    }

    @Test
    void openingAnAccountIsIdempotentToo() {
        var client = Tokens.operator("opener-" + UUID.randomUUID());
        var key = LedgerApi.newKey();

        var first = api.open(client, key, "Carteira", "BRL");
        var retry = api.open(client, key, "Carteira", "BRL");

        assertThat(retry).hasStatus(HttpStatus.CREATED).headers().hasValue("Idempotent-Replayed", "true");
        assertThat(body(retry)).isEqualTo(body(first));
        assertThat(mvc.get().uri("/api/v1/accounts").with(client))
                .bodyJson()
                .extractingPath("$.totalElements")
                .isEqualTo(1);
    }

    @Test
    void everyWriteRequiresAWellFormedKey() {
        var account = api.openAccount(WALLET, "BRL");
        var body = """
                {"accountId": "%s", "amount": "1.00", "currency": "BRL"}
                """.formatted(account);

        assertThat(api.post(WALLET, null, "/api/v1/deposits", body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("Idempotency-Key");
        assertThat(api.post(WALLET, "short", "/api/v1/deposits", body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("Idempotency-Key");
        assertThat(api.open(WALLET, null, "Sem chave", "BRL")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
    }

    private long entriesOf(UUID account) {
        return jdbc.queryForObject("select count(*) from ledger.entry where account_id = ?", Long.class, account);
    }
}
