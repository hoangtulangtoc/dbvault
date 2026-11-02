package dev.dbvault.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

class SignInThrottleTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private final SignInThrottle throttle = new SignInThrottle(3, Duration.ofMinutes(15));

    @BeforeEach
    void setUp() {
        at(NOW);
    }

    @Test
    void anAddressIsLockedAfterTheLastAllowedFailure() {
        fail("198.51.100.1");
        fail("198.51.100.1");
        assertThat(throttle.locked("198.51.100.1")).isFalse();
        fail("198.51.100.1");
        assertThat(throttle.locked("198.51.100.1")).isTrue();
        assertThat(throttle.locked("198.51.100.2")).as("another address is not affected").isFalse();
    }

    @Test
    void theLockEndsOnceTheWindowHasPassed() {
        fail("198.51.100.1");
        fail("198.51.100.1");
        fail("198.51.100.1");
        at(NOW.plus(Duration.ofMinutes(15)));
        assertThat(throttle.locked("198.51.100.1")).isFalse();
    }

    @Test
    void failuresSpreadBeyondTheWindowStartCountingAgain() {
        fail("198.51.100.1");
        fail("198.51.100.1");
        at(NOW.plus(Duration.ofMinutes(16)));
        fail("198.51.100.1");
        assertThat(throttle.locked("198.51.100.1")).isFalse();
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        fail("198.51.100.1");
        fail("198.51.100.1");
        throttle.succeeded(new AuthenticationSuccessEvent(token("198.51.100.1")));
        fail("198.51.100.1");
        assertThat(throttle.locked("198.51.100.1")).isFalse();
    }

    private void fail(String address) {
        throttle.failed(new AuthenticationFailureBadCredentialsEvent(token(address), new BadCredentialsException("no")));
    }

    private void at(Instant instant) {
        throttle.clock = Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static UsernamePasswordAuthenticationToken token(String address) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated("operator", "x");
        token.setDetails(new WebAuthenticationDetails(address, null));
        return token;
    }
}
