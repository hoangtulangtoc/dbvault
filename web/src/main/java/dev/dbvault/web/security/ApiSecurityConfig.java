package dev.dbvault.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@code /api/**} is for scripts and dbvault-cli: the same operator account, presented with HTTP
 * Basic on every request instead of a session. It is checked before the console's chain, which
 * stays the catch-all, so an API caller gets a 401 rather than a redirect to a sign-in page.
 *
 * <p>No session is created and no cookie is read: every request carries its own credentials. CSRF
 * protection exists to stop a browser from replaying a cookie the user already holds, so on this
 * chain it has nothing to protect and is off; a script's {@code POST} needs no token. The console's
 * chain, which does use a session cookie, keeps CSRF on.
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
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
