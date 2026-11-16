package io.github.asmeduardo.secureledger.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is an OAuth 2.0 resource server: every call carries a JWT access token, and its scopes
 * decide which operations the caller may use. Which accounts it may use is decided by ownership in
 * the application services. Anything not listed here is denied.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailsSecurityHandler problems) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAuthority(Scopes.authority(Scopes.READ))
                        .requestMatchers(HttpMethod.POST, "/api/**")
                        .hasAuthority(Scopes.authority(Scopes.OPERATE))
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens are never sent automatically by browsers, so there is no CSRF exposure.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
