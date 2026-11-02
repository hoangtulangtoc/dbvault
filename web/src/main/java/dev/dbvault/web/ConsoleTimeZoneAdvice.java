package dev.dbvault.web;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The time zone the console shows times in, chosen in the sidebar like the language (ADR-042):
 * {@code ?tz=Asia/Ho_Chi_Minh} on any page switches it and is kept in a cookie in that browser.
 * Without a choice it is UTC, as before. It changes only what the console shows; schedules keep
 * their own zone, and the API and logs stay in UTC.
 */
@ControllerAdvice
class ConsoleTimeZoneAdvice {

    static final String COOKIE = "dbvault_tz";
    static final ZoneId DEFAULT = ZoneOffset.UTC;

    /** UTC, then every region's zone, Area/City, sorted. */
    static final List<String> ZONES = Stream.concat(Stream.of("UTC"), ZoneId.getAvailableZoneIds().stream()
                    .filter(id -> id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/"))
                    .sorted())
            .toList();

    @ModelAttribute("consoleZone")
    ZoneId consoleZone(HttpServletRequest request, HttpServletResponse response) {
        Optional<ZoneId> chosen = zone(request.getParameter("tz"));
        if (chosen.isPresent()) {
            response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, chosen.get().getId())
                    .path("/").maxAge(Duration.ofDays(365)).httpOnly(true).sameSite("Lax").build().toString());
            return chosen.get();
        }
        Cookie cookie = WebUtils.getCookie(request, COOKIE);
        return zone(cookie == null ? null : cookie.getValue()).orElse(DEFAULT);
    }

    /** What follows a time on a page: {@code UTC}, or the zone's own name. */
    @ModelAttribute("consoleZoneLabel")
    String consoleZoneLabel(HttpServletRequest request, HttpServletResponse response) {
        ZoneId zone = consoleZone(request, response);
        return zone.normalized().equals(ZoneOffset.UTC) ? "UTC" : zone.getId();
    }

    @ModelAttribute("consoleZones")
    List<String> consoleZones() {
        return ZONES;
    }

    /** Only a zone on the list: anything else is ignored, never an error. */
    private static Optional<ZoneId> zone(String id) {
        if (id == null || !ZONES.contains(id)) {
            return Optional.empty();
        }
        try {
            return Optional.of(id.equals("UTC") ? DEFAULT : ZoneId.of(id));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
