package io.github.asmeduardo.secureledger.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** HTTP calls shared by the functional tests, made on behalf of a given client. */
public final class LedgerApi {

    private final MockMvcTester mvc;

    public LedgerApi(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public UUID openAccount(JwtRequestPostProcessor client, String currency) {
        var response = mvc.post()
                .uri("/api/v1/accounts")
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Conta de teste", "currency": "%s"}
                        """.formatted(currency))
                .exchange();
        assertThat(response).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(Responses.read(response, "$.id"));
    }

    public MvcTestResult deposit(JwtRequestPostProcessor client, UUID account, String amount, String currency) {
        return post(client, "/api/v1/deposits", """
                {"accountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(account, amount, currency));
    }

    public MvcTestResult withdraw(JwtRequestPostProcessor client, UUID account, String amount, String currency) {
        return post(client, "/api/v1/withdrawals", """
                {"accountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(account, amount, currency));
    }

    public MvcTestResult transfer(
            JwtRequestPostProcessor client, UUID source, UUID destination, String amount, String currency) {
        return post(client, "/api/v1/transfers", """
                {"sourceAccountId": "%s", "destinationAccountId": "%s", "amount": "%s", "currency": "%s"}
                """.formatted(source, destination, amount, currency));
    }

    public MvcTestResult post(JwtRequestPostProcessor client, String uri, String body) {
        return mvc.post()
                .uri(uri)
                .with(client)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
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
