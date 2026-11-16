package io.github.asmeduardo.secureledger.security;

import static io.github.asmeduardo.secureledger.support.Tokens.OPERATE;
import static io.github.asmeduardo.secureledger.support.Tokens.READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.LedgerApi;
import io.github.asmeduardo.secureledger.support.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Who may call what. Uses its own MockMvc, without the default token of functional tests. */
@IntegrationTest
class ApiAuthorizationIT {

    private static final String ACCOUNT = """
            {"name": "Conta", "currency": "BRL"}
            """;

    @Autowired
    WebApplicationContext context;

    MockMvcTester mvc;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mvc = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build());
    }

    @ParameterizedTest(name = "{0} {1} without a token is 401")
    @CsvSource({
        "GET,  /api/v1/accounts",
        "GET,  /api/v1/accounts/0195f2a4-7d1e-7c3a-9a55-3f1d2c4b5a69",
        "POST, /api/v1/accounts"
    })
    void everyApiEndpointRequiresABearerToken(String method, String path) {
        var response = mvc.perform(request(HttpMethod.valueOf(method), path));

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response).bodyJson().extractingPath("$.type").isEqualTo("urn:secure-ledger:problem:unauthorized");
    }

    @Test
    void readScopeCanQueryButNotChangeAnything() {
        assertThat(mvc.get().uri("/api/v1/accounts").with(Tokens.client("reader", READ)))
                .hasStatusOk();

        assertThat(mvc.post()
                        .uri("/api/v1/accounts")
                        .with(Tokens.client("reader", READ))
                        .header("Idempotency-Key", LedgerApi.newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:secure-ledger:problem:forbidden");
    }

    @Test
    void operateScopeCanChangeButNotQuery() {
        assertThat(mvc.post()
                        .uri("/api/v1/accounts")
                        .with(Tokens.client("writer", OPERATE))
                        .header("Idempotency-Key", LedgerApi.newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ACCOUNT))
                .hasStatus(HttpStatus.CREATED);
        assertThat(mvc.get().uri("/api/v1/accounts").with(Tokens.client("writer", OPERATE)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theAuditScopeOpensTheBackOfficeAndNothingElse() {
        var auditor = Tokens.client("back-office", Tokens.AUDIT);

        assertThat(mvc.get().uri("/api/v1/admin/audit-events").with(auditor)).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/accounts").with(auditor)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/admin/audit-events").with(Tokens.operator("anyone")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void methodsOutsideTheContractAreDenied() {
        assertThat(mvc.delete().uri("/api/v1/accounts").with(Tokens.operator("anyone")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
