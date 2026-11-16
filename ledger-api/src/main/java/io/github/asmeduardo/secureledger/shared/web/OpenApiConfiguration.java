package io.github.asmeduardo.secureledger.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER_JWT = "bearer-jwt";

    @Bean
    OpenAPI secureLedgerOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_JWT,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                Access token for the audience secure-ledger-api. Scopes: \
                                                ledger.read (query own accounts, statements and audit events), \
                                                ledger.operate (open accounts and move money) and \
                                                ledger.audit (whole audit trail and reconciliation).""")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .info(new Info()
                        .title("Secure Ledger API")
                        .version("v1")
                        .description("""
                                Double-entry ledger for internal credits: accounts, deposits, simulated \
                                withdrawals and transfers. Amounts are decimal strings in the scale of their \
                                currency. Errors follow RFC 9457 (application/problem+json); problem types are \
                                URNs of the form urn:secure-ledger:problem:<code>.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
