package io.github.asmeduardo.secureledger.devauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
        properties = {
            "SECURE_LEDGER_WALLET_CLIENT_SECRET=wallet-test-secret",
            "SECURE_LEDGER_MARKETPLACE_CLIENT_SECRET=marketplace-test-secret",
            "SECURE_LEDGER_AUDITOR_CLIENT_SECRET=auditor-test-secret"
        })
@AutoConfigureMockMvc
class TokenEndpointIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void walletReceivesAJwtForTheLedgerApiWithItsScopes() throws Exception {
        var response = token("demo-wallet", "wallet-test-secret", "ledger.read ledger.operate");

        assertThat(response).hasStatusOk();
        var jwt = jwtDecoder.decode(JsonPath.read(response.getResponse().getContentAsString(), "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo("demo-wallet");
        assertThat(jwt.getAudience()).containsExactly("secure-ledger-api");
        assertThat(jwt.getIssuer()).hasToString("http://localhost:9000");
        assertThat(jwt.getClaimAsStringList("scope")).containsExactlyInAnyOrder("ledger.read", "ledger.operate");
    }

    @Test
    void aClientMayAskForFewerScopes() throws Exception {
        var response = token("demo-marketplace", "marketplace-test-secret", "ledger.read");

        var jwt = jwtDecoder.decode(JsonPath.read(response.getResponse().getContentAsString(), "$.access_token"));
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("ledger.read");
    }

    @Test
    void walletCannotObtainTheAuditScope() {
        assertThat(token("demo-wallet", "wallet-test-secret", "ledger.audit"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.error")
                .isEqualTo("invalid_scope");
    }

    @Test
    void auditorObtainsOnlyTheAuditScope() throws Exception {
        var response = token("demo-auditor", "auditor-test-secret", "ledger.audit");

        var jwt = jwtDecoder.decode(JsonPath.read(response.getResponse().getContentAsString(), "$.access_token"));
        assertThat(jwt.getClaimAsStringList("scope")).containsExactly("ledger.audit");
        assertThat(token("demo-auditor", "auditor-test-secret", "ledger.operate"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void clientMaySendItsSecretInTheRequestBody() {
        var response = mvc.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("grant_type", "client_credentials")
                .formField("scope", "ledger.read")
                .formField("client_id", "demo-wallet")
                .formField("client_secret", "wallet-test-secret")
                .exchange();

        assertThat(response).hasStatusOk().bodyJson().hasPath("$.access_token");
    }

    @Test
    void wrongSecretIsRejected() {
        assertThat(token("demo-wallet", "not-the-secret", "ledger.read")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult token(String clientId, String secret, String scope) {
        return mvc.post()
                .uri("/oauth2/token")
                .with(httpBasic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formField("grant_type", "client_credentials")
                .formField("scope", scope)
                .exchange();
    }
}
