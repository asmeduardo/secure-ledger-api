package io.github.asmeduardo.secureledger.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Access tokens for MockMvc requests, already authenticated, for a given client and scopes. */
public final class Tokens {

    public static final String READ = "ledger.read";
    public static final String OPERATE = "ledger.operate";
    public static final String AUDIT = "ledger.audit";

    private Tokens() {}

    public static JwtRequestPostProcessor client(String clientId, String... scopes) {
        return jwt().jwt(token -> token.subject(clientId))
                .authorities(Arrays.stream(scopes)
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .toArray(GrantedAuthority[]::new));
    }

    /** A client allowed to read and operate its own accounts. */
    public static JwtRequestPostProcessor operator(String clientId) {
        return client(clientId, READ, OPERATE);
    }
}
