package io.github.asmeduardo.secureledger.devauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OAuth 2.0 authorization server for local development only. Clients are configured in {@code
 * application.yml} and their secrets come from the environment or the {@code .env} file; the signing
 * key is generated on every start. Never expose it outside a developer machine.
 */
@SpringBootApplication
public class DevAuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevAuthServerApplication.class, args);
    }
}
