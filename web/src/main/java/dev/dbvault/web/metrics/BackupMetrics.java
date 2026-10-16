package dev.dbvault.web.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.ExecutionStatus;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.HistoryPage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;

/**
 * The backup metrics operators watch from Prometheus (DBV-30), one series per target:
 * <ul>
 *   <li>{@code dbvault_backup_last_success_timestamp_seconds} — when its newest successful backup finished;</li>
 *   <li>{@code dbvault_backup_last_duration_seconds} and {@code dbvault_backup_last_size_bytes} — of that backup;</li>
 *   <li>{@code dbvault_backup_failures_24h} — backups that failed in the last 24 hours.</li>
 * </ul>
 *
 * <p>Read from the execution history, so they survive a restart and say the same as the console.
 * Refreshed every 30 seconds rather than on each scrape: a scrape never waits on the database. The
 * tags are the target's name and engine; a target removed from dbvault drops out at the next refresh.
 *
 * <p>The refresh has its own daemon thread rather than Spring's scheduler, which would add a second
 * {@code Executor} bean beside the backup job executor.
 */
@Component
public class BackupMetrics implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BackupMetrics.class);

    static final Duration WINDOW = Duration.ofHours(24);
    private static final int PAGE = 200;

    private final DatabaseTargetRepository targets;
    private final BackupExecutionRepository executions;
    private final Clock clock;
    private final MultiGauge lastSuccess;
    private final MultiGauge lastDuration;
    private final MultiGauge lastSize;
    private final MultiGauge failures;
    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dbvault-metrics");
        thread.setDaemon(true);
        return thread;
    });

    public BackupMetrics(MeterRegistry registry, DatabaseTargetRepository targets,
            BackupExecutionRepository executions, Clock clock) {
        this.targets = targets;
        this.executions = executions;
        this.clock = clock;
        this.lastSuccess = MultiGauge.builder("dbvault.backup.last.success.timestamp").baseUnit("seconds")
                .description("When the target's newest successful backup finished").register(registry);
        this.lastDuration = MultiGauge.builder("dbvault.backup.last.duration").baseUnit("seconds")
                .description("How long the target's newest successful backup took").register(registry);
        this.lastSize = MultiGauge.builder("dbvault.backup.last.size").baseUnit("bytes")
                .description("Size of the target's newest successful backup").register(registry);
        this.failures = MultiGauge.builder("dbvault.backup.failures.24h")
                .description("Backups of the target that failed in the last 24 hours").register(registry);
        refresh();
        refresher.scheduleWithFixedDelay(this::refreshQuietly, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        refresher.shutdownNow();
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (RuntimeException e) {
            // The previous values stay; the next refresh tries again.
            log.warn("Could not refresh the backup metrics", e);
        }
    }

    public void refresh() {
        Map<UUID, BackupExecution> succeeded = new HashMap<>();
        executions.findLatestSucceededPerTarget().forEach(execution -> succeeded.put(execution.getTargetId(), execution));
        Map<UUID, Integer> failed = failuresSince(clock.instant().minus(WINDOW));

        List<MultiGauge.Row<?>> success = new ArrayList<>();
        List<MultiGauge.Row<?>> duration = new ArrayList<>();
        List<MultiGauge.Row<?>> size = new ArrayList<>();
        List<MultiGauge.Row<?>> failure = new ArrayList<>();
        for (DatabaseTarget target : targets.findAll()) {
            Tags tags = Tags.of("target", target.getName(), "engine", target.getEngine().name());
            failure.add(MultiGauge.Row.of(tags, failed.getOrDefault(target.getId(), 0)));
            BackupExecution latest = succeeded.get(target.getId());
            if (latest == null || latest.getFinishedAt() == null) {
                continue;
            }
            success.add(MultiGauge.Row.of(tags, latest.getFinishedAt().getEpochSecond()));
            duration.add(MultiGauge.Row.of(tags,
                    Duration.between(latest.getStartedAt(), latest.getFinishedAt()).toMillis() / 1000.0));
            if (latest.getSizeBytes() != null) {
                size.add(MultiGauge.Row.of(tags, latest.getSizeBytes()));
            }
        }
        lastSuccess.register(success, true);
        lastDuration.register(duration, true);
        lastSize.register(size, true);
        failures.register(failure, true);
    }

    /** Walks the history newest first until it is older than {@code since}. */
    private Map<UUID, Integer> failuresSince(Instant since) {
        Map<UUID, Integer> failed = new HashMap<>();
        for (int page = 1; ; page++) {
            HistoryPage<BackupExecution> history = executions.findNewestFirst(page, PAGE);
            for (BackupExecution execution : history.items()) {
                if (execution.getStartedAt().isBefore(since)) {
                    return failed;
                }
                if (execution.getStatus() == ExecutionStatus.FAILED) {
                    failed.merge(execution.getTargetId(), 1, Integer::sum);
                }
            }
            if (!history.hasOlder()) {
                return failed;
            }
        }
    }
}
