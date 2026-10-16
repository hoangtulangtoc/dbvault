package dev.dbvault.web.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The unversioned paths of the first API, kept only to say where they went: every method answers
 * {@code 308 Permanent Redirect} to the same path under {@code /api/v1}, marked deprecated. A client
 * that does not follow redirects has to move to {@code /api/v1} itself (ADR-039).
 */
@RestController
public class LegacyApiRedirectController {

    @RequestMapping({ "/api/targets", "/api/targets/**" })
    ResponseEntity<Void> moved(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        String location = "/api/v1" + path.substring("/api".length()) + (query == null ? "" : "?" + query);
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header(HttpHeaders.LOCATION, location)
                .header("Deprecation", "true")
                .header(HttpHeaders.LINK, "<" + location + ">; rel=\"successor-version\"")
                .build();
    }
}
