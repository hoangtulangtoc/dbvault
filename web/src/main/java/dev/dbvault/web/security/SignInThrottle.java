package dev.dbvault.web.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Stops one address from guessing at the operator's password without end. After {@code max-failures}
 * failed sign-ins within {@code lock} of the first, the address may not try again until {@code lock}
 * has passed; a successful sign-in clears its count. The console's form and the API's HTTP Basic count
 * together, since both check the same password.
 *
 * <p>The address is the one the application sees. Behind a reverse proxy that is the proxy's, for every
 * client, unless the proxy's {@code X-Forwarded-For} is believed ({@code SERVER_FORWARD_HEADERS_STRATEGY}),
 * so the deployment guide asks for it. Counts live in memory: a restart forgets them.
 */
class SignInThrottle {

    private record Failures(int count, Instant first) {
    }

    private final int maxFailures;
    private final Duration lock;
    private final Map<String, Failures> byAddress = new ConcurrentHashMap<>();
    Clock clock = Clock.systemUTC();

    SignInThrottle(@Value("${dbvault.sign-in.max-failures:10}") int maxFailures,
            @Value("${dbvault.sign-in.lock:PT15M}") Duration lock) {
        this.maxFailures = maxFailures;
        this.lock = lock;
    }

    Duration lock() {
        return lock;
    }

    boolean locked(String address) {
        Failures failures = byAddress.get(address);
        if (failures == null) {
            return false;
        }
        if (expired(failures)) {
            byAddress.remove(address, failures);
            return false;
        }
        return failures.count() >= maxFailures;
    }

    @EventListener
    void failed(AbstractAuthenticationFailureEvent event) {
        String address = address(event.getAuthentication());
        if (address != null) {
            byAddress.compute(address, (key, failures) -> failures == null || expired(failures)
                    ? new Failures(1, clock.instant())
                    : new Failures(failures.count() + 1, failures.first()));
        }
    }

    @EventListener
    void succeeded(AuthenticationSuccessEvent event) {
        String address = address(event.getAuthentication());
        if (address != null) {
            byAddress.remove(address);
        }
    }

    private boolean expired(Failures failures) {
        return !clock.instant().isBefore(failures.first().plus(lock));
    }

    private static String address(Authentication authentication) {
        return authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : null;
    }
}
