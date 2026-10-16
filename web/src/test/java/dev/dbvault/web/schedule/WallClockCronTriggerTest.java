package dev.dbvault.web.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/** DBV-25: exactly one run on every scheduled day, the days the clocks change included. */
class WallClockCronTriggerTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void aTimeInTheSpringGapRunsAsFarIntoTheDayAsTheJump() throws Exception {
        // 2026-03-08: New York jumps from 02:00 to 03:00, so 02:30 does not exist that day.
        List<Instant> fires = fires("0 30 2 * * ?", NEW_YORK, "2026-03-07T00:00:00Z", 3);

        assertThat(fires).containsExactly(
                Instant.parse("2026-03-07T07:30:00Z"),   // 02:30 EST
                Instant.parse("2026-03-08T07:30:00Z"),   // 03:30 EDT, the day of the jump
                Instant.parse("2026-03-09T06:30:00Z"));  // 02:30 EDT
    }

    @Test
    void aTimeTheAutumnChangeRepeatsRunsOnceAtItsFirstOccurrence() throws Exception {
        // 2026-11-01: New York goes from 02:00 back to 01:00, so 01:30 happens twice.
        List<Instant> fires = fires("0 30 1 * * ?", NEW_YORK, "2026-10-31T00:00:00Z", 3);

        assertThat(fires).containsExactly(
                Instant.parse("2026-10-31T05:30:00Z"),   // 01:30 EDT
                Instant.parse("2026-11-01T05:30:00Z"),   // the first 01:30, EDT
                Instant.parse("2026-11-02T06:30:00Z"));  // 01:30 EST
    }

    @Test
    void aRestartInsideTheRepeatedHourDoesNotRunTheDayAgain() throws Exception {
        WallClockCronTrigger trigger = new WallClockCronTrigger("0 30 1 * * ?", NEW_YORK);
        trigger.setStartTime(Date.from(Instant.parse("2026-10-31T00:00:00Z")));

        // 06:10Z is 01:10 EST: the wall clock reads before 01:30 again, but today's run happened at 05:30Z.
        Date next = trigger.getFireTimeAfter(Date.from(Instant.parse("2026-11-01T06:10:00Z")));

        assertThat(next.toInstant()).isEqualTo(Instant.parse("2026-11-02T06:30:00Z"));
    }

    @Test
    void europeChangesOnItsOwnDays() throws Exception {
        assertThat(fires("0 30 2 * * ?", BERLIN, "2026-03-28T12:00:00Z", 2)).containsExactly(
                Instant.parse("2026-03-29T01:30:00Z"),   // 03:30 CEST, the day of the jump
                Instant.parse("2026-03-30T00:30:00Z"));
        assertThat(fires("0 30 2 * * ?", BERLIN, "2026-10-24T12:00:00Z", 2)).containsExactly(
                Instant.parse("2026-10-25T00:30:00Z"),   // the first 02:30, CEST
                Instant.parse("2026-10-26T01:30:00Z"));
    }

    @Test
    void anOrdinaryDayIsUnchanged() throws Exception {
        assertThat(fires("0 0 2 * * ?", ZoneId.of("Asia/Ho_Chi_Minh"), "2026-10-16T00:00:00Z", 2))
                .containsExactly(Instant.parse("2026-10-16T19:00:00Z"), Instant.parse("2026-10-17T19:00:00Z"));
    }

    private static List<Instant> fires(String cron, ZoneId zone, String from, int count) throws Exception {
        WallClockCronTrigger trigger = new WallClockCronTrigger(cron, zone);
        Date after = Date.from(Instant.parse(from));
        // A trigger never fires before its start, which Quartz sets to "now" by default.
        trigger.setStartTime(after);
        List<Instant> fires = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            after = trigger.getFireTimeAfter(after);
            fires.add(after.toInstant());
        }
        return fires;
    }
}
