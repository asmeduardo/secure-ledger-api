package io.github.asmeduardo.secureledger.accounts;

import static io.github.asmeduardo.secureledger.support.Responses.read;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class StatementApiIT {

    private static final JwtRequestPostProcessor WALLET = Tokens.operator("statement-wallet");

    @Autowired
    MockMvcTester mvc;

    LedgerApi api;

    @BeforeEach
    void setUp() {
        api = new LedgerApi(mvc);
    }

    @Test
    void listsEntriesNewestFirstWithTheBalanceAfterEach() {
        var account = api.openAccount(WALLET, "BRL");
        var other = api.openAccount(WALLET, "BRL");
        api.deposit(WALLET, account, "100.00", "BRL");
        api.withdraw(WALLET, account, "30.00", "BRL");
        String transferId = read(api.transfer(WALLET, account, other, "20.00", "BRL"), "$.id");

        var statement = statement(account, 10, null);

        assertThat(statement).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"items": [
                  {"transactionId": "%s", "type": "TRANSFER", "direction": "DEBIT", "amount": "20.00",
                   "balanceAfter": "50.00"},
                  {"type": "WITHDRAWAL", "direction": "DEBIT", "amount": "30.00", "balanceAfter": "70.00"},
                  {"type": "DEPOSIT", "direction": "CREDIT", "amount": "100.00", "balanceAfter": "100.00"}
                ],
                 "nextCursor": null}
                """.formatted(transferId));
        assertThat(statement).bodyJson().hasPath("$.items[0].postedAt");
    }

    @Test
    void entriesPostedWhileAClientPagesNeverShiftTheNextPages() {
        var account = api.openAccount(WALLET, "BRL");
        for (int i = 1; i <= 5; i++) {
            api.deposit(WALLET, account, i + ".00", "BRL");
        }

        var amounts = new ArrayList<String>();
        var page = statement(account, 2, null);
        amounts.addAll(amountsOf(page));
        api.deposit(WALLET, account, "6.00", "BRL");
        api.deposit(WALLET, account, "7.00", "BRL");
        while (read(page, "$.nextCursor") != null) {
            page = statement(account, 2, read(page, "$.nextCursor"));
            amounts.addAll(amountsOf(page));
        }

        assertThat(amounts).containsExactly("5.00", "4.00", "3.00", "2.00", "1.00");
    }

    @Test
    void theStatementOfAnotherClientIsNotFound() {
        var foreign = api.fundedAccount(Tokens.operator("statement-owner"), "BRL", "10.00");

        assertThat(mvc.get().uri("/api/v1/accounts/{id}/entries", foreign).with(WALLET))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:account-not-found");
        assertThat(mvc.get()
                        .uri("/api/v1/accounts/{id}/entries", UUID.randomUUID())
                        .with(WALLET))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void cursorAndLimitAreValidated() {
        var account = api.openAccount(WALLET, "BRL");

        assertThat(statement(account, 10, "bogus"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:invalid-cursor");
        assertThat(statement(account, 101, null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[0].field")
                .isEqualTo("limit");
    }

    private MvcTestResult statement(UUID account, int limit, String cursor) {
        var uri =
                "/api/v1/accounts/" + account + "/entries?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor);
        return mvc.get().uri(uri).with(WALLET).exchange();
    }

    private static List<String> amountsOf(MvcTestResult page) {
        return read(page, "$.items[*].amount");
    }
}
