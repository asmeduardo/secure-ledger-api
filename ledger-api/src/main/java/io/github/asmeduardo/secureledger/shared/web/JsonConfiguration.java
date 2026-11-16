package io.github.asmeduardo.secureledger.shared.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JsonConfiguration {

    /**
     * Amounts are written as JSON strings ("10.50"), so no client parses them into a binary floating
     * point number. Requests may send either a string or a number; both are read as exact decimals.
     */
    @Bean
    JsonMapperBuilderCustomizer decimalsAsStrings() {
        return builder -> builder.withConfigOverride(
                BigDecimal.class, override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
    }
}
