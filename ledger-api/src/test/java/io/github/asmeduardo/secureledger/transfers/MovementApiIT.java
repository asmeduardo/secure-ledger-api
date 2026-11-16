package io.github.asmeduardo.secureledger.transfers;

import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class MovementApiIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("movement-wallet");
    private static final JwtRequestPostProcessor MARKETPLACE = Tokens.operator("movement-marketplace");

    @Autowired
    MockMvcTester mvc;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void depositCreditsTheAccountAndShowsBothEntries() {
        var account = api.openAccount(WALLET, "BRL");

        var response = api.deposit(WALLET, account, "100.00", "BRL");

        assertThat(response).hasStatus(HttpStatus.CREATED);
        String id = read(response, "$.id");
        assertThat(response.getResponse().getHeader("Location")).isEqualTo("/api/v1/transactions/" + id);
        assertThat(response).bodyJson().isLenientlyEqualTo("""
                {"type": "DEPOSIT", "amount": "100.00", "currency": "BRL",
                 "entries": [
                   {"accountId": "00000000-0000-4000-8000-000000000986", "direction": "DEBIT", "amount": "100.00"},
                   {"accountId": "%s", "direction": "CREDIT", "amount": "100.00", "balanceAfter": "100.00"}
                 ]}
                """.formatted(account));
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("100.00");
    }

    @Test
    void withdrawalDebitsTheAccount() {
        var account = api.fundedAccount(WALLET, "BRL", "80.00");

        var response = api.withdraw(WALLET, account, "30.00", "BRL");

        assertThat(response)
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("WITHDRAWAL");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("50.00");
    }

    @Test
    void withdrawalBeyondTheBalanceIsRefusedAndMovesNothing() {
        var account = api.fundedAccount(WALLET, "BRL", "10.00");

        assertProblem(
                api.withdraw(WALLET, account, "10.01", "BRL"), HttpStatus.UNPROCESSABLE_CONTENT, "insufficient-funds");
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("10.00");
    }

    @Test
    void transferMovesMoneyBetweenTheClientsOwnAccounts() {
        var source = api.fundedAccount(WALLET, "EUR", "70.00");
        var destination = api.openAccount(WALLET, "EUR");

        var response = api.transfer(WALLET, source, destination, "25.10", "EUR");

        assertThat(response).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"type": "TRANSFER", "amount": "25.10", "currency": "EUR",
                 "entries": [
                   {"accountId": "%s", "direction": "DEBIT", "amount": "25.10", "balanceAfter": "44.90"},
                   {"accountId": "%s", "direction": "CREDIT", "amount": "25.10", "balanceAfter": "25.10"}
                 ]}
                """.formatted(
                        source, destination));
        assertThat(api.balance(WALLET, source)).isEqualByComparingTo("44.90");
        assertThat(api.balance(WALLET, destination)).isEqualByComparingTo("25.10");
    }

    @Test
    void aClientCannotMoveMoneyOfAnotherClient() {
        var foreign = api.fundedAccount(MARKETPLACE, "BRL", "500.00");
        var own = api.fundedAccount(WALLET, "BRL", "5.00");

        assertProblem(api.withdraw(WALLET, foreign, "1.00", "BRL"), HttpStatus.NOT_FOUND, "account-not-found");
        assertProblem(api.deposit(WALLET, foreign, "1.00", "BRL"), HttpStatus.NOT_FOUND, "account-not-found");
        assertProblem(api.transfer(WALLET, foreign, own, "1.00", "BRL"), HttpStatus.NOT_FOUND, "account-not-found");
        assertProblem(api.transfer(WALLET, own, foreign, "1.00", "BRL"), HttpStatus.NOT_FOUND, "account-not-found");
        assertThat(api.balance(MARKETPLACE, foreign)).isEqualByComparingTo("500.00");
        assertThat(api.balance(WALLET, own)).isEqualByComparingTo("5.00");
    }

    @Test
    void theCurrencyOfTheRequestMustBeTheCurrencyOfTheAccounts() {
        var reais = api.fundedAccount(WALLET, "BRL", "5.00");
        var dollars = api.openAccount(WALLET, "USD");

        assertProblem(api.deposit(WALLET, reais, "1.00", "USD"), HttpStatus.UNPROCESSABLE_CONTENT, "currency-mismatch");
        assertProblem(
                api.transfer(WALLET, reais, dollars, "1.00", "BRL"),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "currency-mismatch");
    }

    @Test
    void anAccountCannotTransferToItself() {
        var account = api.fundedAccount(WALLET, "BRL", "5.00");

        assertProblem(
                api.transfer(WALLET, account, account, "1.00", "BRL"),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "invalid-transaction");
    }

    @Test
    void amountsArePositiveWithAtMostTwoDecimals() {
        var account = api.openAccount(WALLET, "BRL");

        assertThat(api.deposit(WALLET, account, "10.001", "BRL"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("amount");
        assertThat(api.deposit(WALLET, account, "0", "BRL")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.deposit(WALLET, account, "-5", "BRL")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.balance(WALLET, account)).isEqualByComparingTo("0.00");
    }

    @Test
    void aTransactionIsVisibleOnlyToTheOwnerOfItsAccounts() {
        var account = api.openAccount(WALLET, "BRL");
        String id = read(api.deposit(WALLET, account, "12.00", "BRL"), "$.id");

        assertThat(mvc.get().uri("/api/v1/transactions/{id}", id).with(WALLET))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.amount")
                .isEqualTo("12.00");
        assertProblem(
                mvc.get().uri("/api/v1/transactions/{id}", id).with(MARKETPLACE).exchange(),
                HttpStatus.NOT_FOUND,
                "transaction-not-found");
        assertProblem(
                mvc.get()
                        .uri("/api/v1/transactions/{id}", UUID.randomUUID())
                        .with(WALLET)
                        .exchange(),
                HttpStatus.NOT_FOUND,
                "transaction-not-found");
    }

    private static void assertProblem(MvcTestResult response, HttpStatus status, String code) {
        assertThat(response)
                .hasStatus(status)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:" + code);
    }
}
