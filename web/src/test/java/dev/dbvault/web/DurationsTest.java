package dev.dbvault.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DurationsTest {

    private static final Instant START = Instant.parse("2026-11-09T02:00:00Z");

    private final Durations durations = new Durations();

    @Test
    void readsLikeAPersonWould() {
        assertThat(durations.readable(START, START)).isEqualTo("0 s");
        assertThat(durations.readable(START, START.plusSeconds(59))).isEqualTo("59 s");
        assertThat(durations.readable(START, START.plusSeconds(60))).isEqualTo("1 min");
        assertThat(durations.readable(START, START.plusSeconds(90))).isEqualTo("1 min 30 s");
        assertThat(durations.readable(START, START.plusSeconds(3599))).isEqualTo("59 min 59 s");
        assertThat(durations.readable(START, START.plusSeconds(3600))).isEqualTo("1 h");
        assertThat(durations.readable(START, START.plusSeconds(7 * 3600 + 5 * 60 + 9))).isEqualTo("7 h 5 min");
    }

    /** A running backup has no finish time yet; a clock step back must not print a negative time. */
    @Test
    void noFinishTimeMeansNoDuration() {
        assertThat(durations.readable(START, null)).isEqualTo("—");
        assertThat(durations.readable(START, START.minusSeconds(1))).isEqualTo("—");
    }
}
