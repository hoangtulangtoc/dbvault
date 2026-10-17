package dev.dbvault.web.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.HistoryPage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class BackupMetricsTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID LOGS = UUID.randomUUID();

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DatabaseTargetRepository targets = mock(DatabaseTargetRepository.class);
    private final BackupExecutionRepository executions = mock(BackupExecutionRepository.class);

    @Test
    void reportsTheNewestSuccessAndTheFailuresOfTheLastDayPerTarget() {
        BackupExecution good = BackupExecution.started(UUID.randomUUID(), SHOP, NOW.minusSeconds(3600))
                .succeeded("/backups/shop.sql.gz", 2048, "ab".repeat(32), NOW.minusSeconds(3600 - 90));
        BackupExecution recentFailure = BackupExecution.started(UUID.randomUUID(), SHOP, NOW.minusSeconds(600))
                .failed("Access denied", NOW.minusSeconds(590));
        BackupExecution oldFailure = BackupExecution.started(UUID.randomUUID(), LOGS, NOW.minusSeconds(26 * 3600))
                .failed("Timed out", NOW.minusSeconds(26 * 3600 - 5));
        when(targets.findAll()).thenReturn(List.of(target(SHOP, "shop"), target(LOGS, "logs")));
        when(executions.findLatestSucceededPerTarget()).thenReturn(List.of(good));
        when(executions.findNewestFirst(anyInt(), anyInt()))
                .thenReturn(new HistoryPage<>(List.of(recentFailure, good, oldFailure), 1, false));

        new BackupMetrics(registry, targets, executions, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(gauge("dbvault.backup.failures.24h", "shop")).isEqualTo(1);
        assertThat(gauge("dbvault.backup.failures.24h", "logs")).isZero();
        assertThat(gauge("dbvault.backup.last.success.timestamp", "shop")).isEqualTo(NOW.minusSeconds(3510).getEpochSecond());
        assertThat(gauge("dbvault.backup.last.duration", "shop")).isEqualTo(90);
        assertThat(gauge("dbvault.backup.last.size", "shop")).isEqualTo(2048);
        // A target that never succeeded has no "last success" to report.
        assertThat(registry.find("dbvault.backup.last.success.timestamp").tag("target", "logs").gauge()).isNull();
    }

    @Test
    void aRemovedTargetDropsOutAtTheNextRefresh() {
        when(targets.findAll()).thenReturn(List.of(target(SHOP, "shop")));
        when(executions.findLatestSucceededPerTarget()).thenReturn(List.of());
        when(executions.findNewestFirst(anyInt(), anyInt())).thenReturn(new HistoryPage<>(List.of(), 1, false));
        BackupMetrics metrics = new BackupMetrics(registry, targets, executions, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(registry.find("dbvault.backup.failures.24h").tag("target", "shop").gauge()).isNotNull();

        when(targets.findAll()).thenReturn(List.of());
        metrics.refresh();

        assertThat(registry.find("dbvault.backup.failures.24h").tag("target", "shop").gauge()).isNull();
    }

    private double gauge(String name, String target) {
        return registry.get(name).tag("target", target).gauge().value();
    }

    private static DatabaseTarget target(UUID id, String name) {
        return DatabaseTarget.builder().id(id).name(name).engine(DatabaseEngine.MYSQL).host("db").port(3306)
                .databaseName(name).username("backup").passwordCiphertext("sealed").createdAt(NOW).build();
    }
}
