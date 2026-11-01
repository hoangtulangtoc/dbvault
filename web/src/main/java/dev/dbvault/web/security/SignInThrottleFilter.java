package dev.dbvault.web.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns away a sign-in from a locked address before its password is checked: the console's form goes back
 * to the sign-in page with {@code ?locked}, an API call with credentials gets {@code 429}. Anything else
 * passes, so a signed-in session from that address keeps working.
 */
class SignInThrottleFilter extends OncePerRequestFilter {

    private final SignInThrottle throttle;

    SignInThrottleFilter(SignInThrottle throttle) {
        this.throttle = throttle;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean formSignIn = "POST".equals(request.getMethod())
                && (request.getContextPath() + "/login").equals(request.getRequestURI());
        boolean basicSignIn = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        if ((formSignIn || basicSignIn) && throttle.locked(request.getRemoteAddr())) {
            if (formSignIn) {
                response.sendRedirect(request.getContextPath() + "/login?locked");
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(throttle.lock().toSeconds()));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Too many failed sign-ins from this address; try again later\"}");
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
