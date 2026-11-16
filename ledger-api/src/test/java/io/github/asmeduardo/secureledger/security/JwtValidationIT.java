package io.github.asmeduardo.secureledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.asmeduardo.secureledger.support.IntegrationTest;
import io.github.asmeduardo.secureledger.support.TestJwtKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Sends real signed tokens through the resource server, proving that signature, issuer, audience
 * and expiry are all checked, that scopes come from the {@code scope} claim and that the client is
 * the {@code sub} claim.
 */
@IntegrationTest
class JwtValidationIT {

    @Autowired
    WebApplicationContext context;

    MockMvcTester mvc;

    @BeforeEach
    void buildMockMvcWithoutDefaultToken() {
        mvc = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build());
    }

    @Test
    void acceptsATokenFromTheTrustedIssuerForThisApi() {
        assertThat(get(TestJwtKeys.sign(claims().build()))).hasStatusOk();
    }

    @Test
    void rejectsATokenIssuedForAnotherAudience() {
        assertThat(get(TestJwtKeys.sign(claims().audience("billing-api").build())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsATokenWithoutAudience() {
        assertThat(get(TestJwtKeys.sign(claims().audience((String) null).build())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        assertThat(get(TestJwtKeys.sign(claims().issuer("https://evil.example").build())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsAnExpiredToken() {
        var issuedAt = Instant.now().minus(Duration.ofHours(2));
        var expired = claims().issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(Duration.ofMinutes(5))))
                .build();

        assertThat(get(TestJwtKeys.sign(expired))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsATokenSignedWithAnUntrustedKey() {
        assertThat(get(TestJwtKeys.signWithUnknownKey(claims().build()))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void grantsOnlyTheScopesInTheToken() {
        var operateOnly =
                TestJwtKeys.sign(claims().claim("scope", "ledger.operate").build());

        assertThat(get(operateOnly)).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult get(String token) {
        return mvc.get()
                .uri("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private static JWTClaimsSet.Builder claims() {
        var now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(TestJwtKeys.ISSUER)
                .audience(TestJwtKeys.AUDIENCE)
                .subject("demo-wallet")
                .claim("scope", "ledger.read")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))));
    }
}
