package io.github.asmeduardo.secureledger.accounts;

import static io.github.asmeduardo.secureledger.support.Responses.body;
import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.ledger.api.Ledger;
import io.github.asmeduardo.secureledger.shared.money.Money;
import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AccountApiIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Ledger ledger;

    @Test
    void opensAnAccountWithAZeroBalanceInItsCurrency() {
        var response = open(Tokens.operator("wallet-app"), "Carteira de Ana", "BRL");

        assertThat(response).hasStatus(HttpStatus.CREATED);
        String id = read(response, "$.id");
        assertThat(response.getResponse().getHeader("Location")).isEqualTo("/api/v1/accounts/" + id);
        assertThat(response).bodyJson().isLenientlyEqualTo("""
                        {"name": "Carteira de Ana", "currency": "BRL", "balance": "0.00"}
                        """);
        assertThat(response).bodyJson().hasPath("$.createdAt");
    }

    @Test
    void showsTheCurrentBalanceOfTheClientsOwnAccount() {
        var id = openedAccount(Tokens.operator("wallet-app"), "USD");
        ledger.deposit(id, Money.of("25.5", "USD"), "wallet-app", null);

        var response = mvc.get().uri("/api/v1/accounts/{id}", id).with(Tokens.operator("wallet-app"));

        assertThat(response).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"currency": "USD", "balance": "25.50"}
                """);
    }

    @Test
    void listsOnlyTheAccountsOfTheCallingClient() {
        var client = "lister-" + UUID.randomUUID();
        var first = openedAccount(Tokens.operator(client), "BRL");
        var second = openedAccount(Tokens.operator(client), "EUR");
        openedAccount(Tokens.operator("someone-else"), "BRL");

        var response = mvc.get().uri("/api/v1/accounts?page=0&size=10").with(Tokens.operator(client));

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(response)
                .bodyJson()
                .extractingPath("$.content[*].id")
                .asArray()
                .containsExactly(first.toString(), second.toString());
    }

    @Test
    void anAccountOfAnotherClientLooksExactlyLikeOneThatDoesNotExist() {
        var foreign = openedAccount(Tokens.operator("owner-client"), "BRL");
        var unknown = UUID.randomUUID();

        var ofAnotherClient = mvc.get()
                .uri("/api/v1/accounts/{id}", foreign)
                .with(Tokens.operator("intruder"))
                .exchange();
        var missing = mvc.get()
                .uri("/api/v1/accounts/{id}", unknown)
                .with(Tokens.operator("intruder"))
                .exchange();

        assertThat(ofAnotherClient)
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:account-not-found");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(body(ofAnotherClient).replace(foreign.toString(), "<id>"))
                .isEqualTo(body(missing).replace(unknown.toString(), "<id>"));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var response = mvc.post()
                .uri("/api/v1/accounts")
                .header("Idempotency-Key", LedgerApi.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "currency": "real"}
                        """);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.type").isEqualTo("urn:secure-ledger:problem:invalid-request");
        assertThat(response)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("currency", "name");
    }

    @Test
    void refusesACurrencyTheLedgerDoesNotKeep() {
        assertThat(open(Tokens.operator("wallet-app"), "Conta em iene", "JPY"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:unsupported-currency");
    }

    @Test
    void malformedJsonIsAProblemToo() {
        assertThat(mvc.post()
                        .uri("/api/v1/accounts")
                        .header("Idempotency-Key", LedgerApi.newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:malformed-request");
    }

    private MvcTestResult open(JwtRequestPostProcessor token, String name, String currency) {
        return mvc.post()
                .uri("/api/v1/accounts")
                .with(token)
                .header("Idempotency-Key", LedgerApi.newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "currency": "%s"}
                        """.formatted(name, currency))
                .exchange();
    }

    private UUID openedAccount(JwtRequestPostProcessor token, String currency) {
        var response = open(token, "Conta de teste", currency);
        assertThat(response).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(read(response, "$.id"));
    }
}
