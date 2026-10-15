package dev.dbvault.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@code /api/**} is for scripts and dbvault-cli: the same operator account, presented with HTTP
 * Basic on every request instead of a session. It is checked before the console's chain, which
 * stays the catch-all, so an API caller gets a 401 rather than a redirect to a sign-in page.
 *
 * <p>No session is created, so there is no cookie for a browser to replay; CSRF protection stays on
 * and applies to anything here that is not a GET.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
