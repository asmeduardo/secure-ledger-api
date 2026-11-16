package io.github.asmeduardo.secureledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import io.github.asmeduardo.secureledger.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** The OpenAPI contract and Swagger UI are public and describe how to authenticate. */
@IntegrationTest
class ApiDocumentationIT {

    @Autowired
    WebApplicationContext context;

    MockMvcTester mvc;

    @BeforeEach
    void buildMockMvcWithoutToken() {
        mvc = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build());
    }

    @Test
    void swaggerUiAndTheContractArePublic() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }

    @Test
    void contractDocumentsTheBearerSchemeAndItsScopes() {
        var contract = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(contract)
                .bodyJson()
                .extractingPath("$.components.securitySchemes.bearer-jwt.scheme")
                .isEqualTo("bearer");
        assertThat(contract)
                .bodyJson()
                .extractingPath("$.components.securitySchemes.bearer-jwt.description")
                .asString()
                .contains("ledger.read", "ledger.operate", "ledger.audit");
    }

    @Test
    void contractListsTheOperationsOfEveryModule() {
        var contract = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(contract)
                .bodyJson()
                .extractingPath("$.paths")
                .asMap()
                .containsKeys(
                        "/api/v1/accounts",
                        "/api/v1/accounts/{id}",
                        "/api/v1/deposits",
                        "/api/v1/withdrawals",
                        "/api/v1/transfers",
                        "/api/v1/transactions/{id}");
    }

    @Test
    void contractDocumentsProblemResponses() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .bodyJson()
                .extractingPath("$.paths['/api/v1/transfers'].post.responses['422'].content")
                .asMap()
                .containsKey("application/problem+json");
    }
}
