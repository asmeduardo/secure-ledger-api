package io.github.asmeduardo.secureledger.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * HTTP calls shared by the functional tests, made on behalf of a given client. Every write sends a
 * fresh {@code Idempotency-Key} unless the test chooses one.
 */
public final class LedgerApi {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final MockMvcTester mvc;

    public LedgerApi(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    public MvcTestResult open(JwtRequestPostProcessor client, String key, String name, String currency) {
        return post(client, key, "/api/v1/accounts", """
                {"name": "%s", "currency": "%s"}
                """.formatted(name, currency));
    }

    public UUID openAccount(JwtRequestPostProcessor client, String currency) {
        var response = open(client, newKey(), "Conta de teste", currency);
        assertThat(response).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(Responses.read(response, "$.id"));
    }

    public MvcTestResult deposit(JwtRequestPostProcessor client, UUID account, String amount, String currency) {
        return deposit(client, newKey(), account, amount, currency);
    }

    public MvcTestResult deposit(
            JwtRequestPostProcessor client, String key, UUID account, String amount, String currency) {
        return post(client, key, "/api/v1/deposits", """
                {"accountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(account, amount, currency));
    }

    public MvcTestResult withdraw(JwtRequestPostProcessor client, UUID account, String amount, String currency) {
        return withdraw(client, newKey(), account, amount, currency);
    }

    public MvcTestResult withdraw(
            JwtRequestPostProcessor client, String key, UUID account, String amount, String currency) {
        return post(client, key, "/api/v1/withdrawals", """
                {"accountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(account, amount, currency));
    }

    public MvcTestResult transfer(
            JwtRequestPostProcessor client, UUID source, UUID destination, String amount, String currency) {
        return transfer(client, newKey(), source, destination, amount, currency);
    }

    public MvcTestResult transfer(
            JwtRequestPostProcessor client, String key, UUID source, UUID destination, String amount, String currency) {
        return post(client, key, "/api/v1/transfers", """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(source, destination, amount, currency));
    }

    public MvcTestResult post(JwtRequestPostProcessor client, String key, String uri, String body) {
        var request = mvc.post().uri(uri).with(client).contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request.header(IDEMPOTENCY_KEY, key);
        }
        return request.content(body).exchange();
    }

    /** An account of the client that already holds the given amount. */
    public UUID fundedAccount(JwtRequestPostProcessor client, String currency, String amount) {
        var account = openAccount(client, currency);
        assertThat(deposit(client, account, amount, currency)).hasStatus(HttpStatus.CREATED);
        return account;
    }

    public BigDecimal balance(JwtRequestPostProcessor client, UUID account) {
        var response =
                mvc.get().uri("/api/v1/accounts/{id}", account).with(client).exchange();
        assertThat(response).hasStatusOk();
        return new BigDecimal(Responses.<String>read(response, "$.balance"));
    }
}
