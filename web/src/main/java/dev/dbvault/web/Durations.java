package dev.dbvault.web;

import java.time.Duration;
import java.time.Instant;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * How long a job took, for the templates: {@code ${durations.readable(startedAt, finishedAt)}}.
 * Seconds under a minute, minutes and seconds under an hour, hours and minutes after that; a job
 * without a finish time has no duration yet. Offered to every page like {@link ByteSizes}.
 */
@ControllerAdvice
public class Durations {

    @ModelAttribute("durations")
    Durations durations() {
        return this;
    }

    public String readable(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null || finishedAt.isBefore(startedAt)) {
            return "—";
        }
        long seconds = Duration.between(startedAt, finishedAt).toSeconds();
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            long rest = seconds % 60;
            return seconds / 60 + " min" + (rest == 0 ? "" : " " + rest + " s");
        }
        long minutes = seconds / 60 % 60;
        return seconds / 3600 + " h" + (minutes == 0 ? "" : " " + minutes + " min");
    }
}
