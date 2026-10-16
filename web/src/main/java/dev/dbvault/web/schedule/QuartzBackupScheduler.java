package dev.dbvault.web.schedule;

import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import dev.dbvault.core.exception.InvalidScheduleException;
import dev.dbvault.core.model.BackupSchedule;
import dev.dbvault.core.port.BackupSchedulerPort;

import lombok.RequiredArgsConstructor;

/** Quartz materialization of the schedule definitions stored by the domain. */
@Component
@RequiredArgsConstructor
public class QuartzBackupScheduler implements BackupSchedulerPort {

    static final String SCHEDULE_ID = "scheduleId";
    private static final String GROUP = "backup-schedules";

    private final Scheduler scheduler;

    @Override
    public void validate(BackupSchedule schedule) {
        if (!CronExpression.isValidExpression(schedule.getCronExpression())) {
            throw new InvalidScheduleException(
                    "cronExpression", "This is not a valid Quartz cron expression");
        }
    }

    @Override
    public void replace(BackupSchedule schedule) {
        validate(schedule);
        if (!schedule.isEnabled()) {
            delete(schedule.getId());
            return;
        }

        JobKey jobKey = jobKey(schedule.getId());
        TriggerKey triggerKey = triggerKey(schedule.getId());
        JobDetail job = JobBuilder.newJob(QuartzBackupJob.class)
                .withIdentity(jobKey)
                .usingJobData(SCHEDULE_ID, schedule.getId().toString())
                .storeDurably()
                .build();
        Trigger trigger = trigger(schedule, jobKey, triggerKey);
        try {
            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException e) {
            throw unavailable("replace", schedule.getId(), e);
        }
    }

    @Override
    public void delete(UUID scheduleId) {
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException e) {
            throw unavailable("delete", scheduleId, e);
        }
    }

    @Override
    public Optional<Instant> nextFireTime(UUID scheduleId) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(scheduleId));
            return trigger == null || trigger.getNextFireTime() == null
                    ? Optional.empty()
                    : Optional.of(trigger.getNextFireTime().toInstant());
        } catch (SchedulerException e) {
            throw unavailable("read", scheduleId, e);
        }
    }

    @Override
    public void reconcile(List<BackupSchedule> schedules) {
        schedules.forEach(this::validate);
        Set<JobKey> wanted = new HashSet<>();
        schedules.stream().filter(BackupSchedule::isEnabled)
                .map(schedule -> jobKey(schedule.getId()))
                .forEach(wanted::add);
        try {
            for (JobKey existing : scheduler.getJobKeys(jobGroupEquals(GROUP))) {
                if (!wanted.contains(existing)) {
                    scheduler.deleteJob(existing);
                }
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Quartz could not reconcile backup schedules", e);
        }
        schedules.forEach(this::replace);
    }

    /** Once on every day the expression names, daylight saving changes included (DBV-25). */
    private static Trigger trigger(BackupSchedule schedule, JobKey jobKey, TriggerKey triggerKey) {
        WallClockCronTrigger trigger;
        try {
            trigger = new WallClockCronTrigger(schedule.getCronExpression(), ZoneId.of(schedule.getZoneId()));
        } catch (ParseException e) {
            throw new InvalidScheduleException("cronExpression", "This is not a valid Quartz cron expression");
        }
        trigger.setKey(triggerKey);
        trigger.setJobKey(jobKey);
        trigger.setStartTime(new Date());
        // A stopped application does not make a burst of old backups when it returns. The next
        // future fire wins.
        trigger.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        return trigger;
    }

    private static JobKey jobKey(UUID id) {
        return JobKey.jobKey(id.toString(), GROUP);
    }

    private static TriggerKey triggerKey(UUID id) {
        return TriggerKey.triggerKey(id.toString(), GROUP);
    }

    private static IllegalStateException unavailable(String operation, UUID id, SchedulerException e) {
        return new IllegalStateException(
                "Quartz could not %s backup schedule %s".formatted(operation, id), e);
    }
}
