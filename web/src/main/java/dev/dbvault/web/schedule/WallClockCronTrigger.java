package dev.dbvault.web.schedule;

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import org.quartz.CronExpression;
import org.quartz.impl.triggers.CronTriggerImpl;

/**
 * A Quartz cron trigger that fires once on every day its expression names, also on the days the
 * schedule's zone changes its clocks (DBV-25).
 *
 * <p>Quartz's own cron trigger skips a time that falls in a spring-forward gap: {@code 0 30 2 * * ?}
 * in {@code America/New_York} does not run at all on the day the clocks jump from 02:00 to 03:00.
 * Here the expression is matched against wall-clock time, and the wall-clock time it names is then
 * placed in the zone the way {@link ZonedDateTime#ofLocal} does: a time the zone skips runs as far
 * into the day as the jump, so 02:30 runs at 03:30; a time the autumn change repeats runs once, at
 * its first occurrence.
 *
 * <p>Quartz asks {@link #getFireTimeAfter} for the first fire, after every fire and after a misfire,
 * so overriding it is enough; everything else, misfire handling included, is Quartz's.
 */
final class WallClockCronTrigger extends CronTriggerImpl {

    private static final long serialVersionUID = 1L;

    private final ZoneId zone;
    /** The same expression, evaluated in UTC so that its fields read as wall-clock values. */
    private final CronExpression wallClock;

    WallClockCronTrigger(String cronExpression, ZoneId zone) throws ParseException {
        this.zone = zone;
        this.wallClock = new CronExpression(cronExpression);
        this.wallClock.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        setCronExpression(cronExpression);
        setTimeZone(TimeZone.getTimeZone(zone));
    }

    @Override
    public Date getFireTimeAfter(Date afterTime) {
        Instant after = (afterTime != null ? afterTime : new Date()).toInstant();
        if (getStartTime() != null && after.isBefore(getStartTime().toInstant())) {
            after = getStartTime().toInstant().minusSeconds(1);
        }
        // Start one day back in wall-clock time: after the autumn change the wall clock repeats an
        // hour, and a wall-clock time later than "now" may already have fired an hour ago.
        LocalDateTime from = LocalDateTime.ofInstant(after, zone).minusDays(1);
        Date candidate = wallClock.getTimeAfter(Date.from(from.toInstant(ZoneOffset.UTC)));
        while (candidate != null) {
            LocalDateTime wallTime = LocalDateTime.ofInstant(candidate.toInstant(), ZoneOffset.UTC);
            Instant fire = ZonedDateTime.ofLocal(wallTime, zone, null).toInstant();
            if (fire.isAfter(after)) {
                return getEndTime() != null && fire.isAfter(getEndTime().toInstant()) ? null : Date.from(fire);
            }
            candidate = wallClock.getTimeAfter(candidate);
        }
        return null;
    }
}
