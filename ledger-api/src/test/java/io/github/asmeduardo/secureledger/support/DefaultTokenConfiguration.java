package io.github.asmeduardo.secureledger.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Functional integration tests act as one trusted client with read and operate scopes, so they can
 * focus on behaviour. A request may still name another client with {@link Tokens}. Authorization
 * itself is covered by tests that build their own {@code MockMvc} without this token.
 */
@TestConfiguration(proxyBeanMethods = false)
class DefaultTokenConfiguration {

    static final String CLIENT_ID = "integration-test-client";

    @Bean
    MockMvcBuilderCustomizer operatorTokenOnEveryRequest() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(Tokens.operator(CLIENT_ID)));
    }
}
