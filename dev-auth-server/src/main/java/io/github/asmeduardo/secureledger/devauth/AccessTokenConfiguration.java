package io.github.asmeduardo.secureledger.devauth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration(proxyBeanMethods = false)
class AccessTokenConfiguration {

    /** Audience the ledger API requires; tokens minted for anything else are refused there. */
    static final String LEDGER_API = "secure-ledger-api";

    /**
     * By default the audience of an access token is the client id. The API instead checks that the
     * token was issued for it, so every access token names the API as its audience.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> ledgerApiAudience() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(List.of(LEDGER_API));
            }
        };
    }
}
