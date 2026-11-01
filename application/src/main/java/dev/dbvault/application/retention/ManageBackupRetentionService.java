package dev.dbvault.application.retention;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.BackupRetentionPolicy;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.core.port.BackupRetentionPolicyRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;

import lombok.RequiredArgsConstructor;

/** Enables, changes and disables per-target automatic retention. */
@Service
@RequiredArgsConstructor
public class ManageBackupRetentionService {

    public record RetentionView(DatabaseTarget target, Optional<BackupRetentionPolicy> policy) {
    }

    private final BackupRetentionPolicyRepository policies;
    private final DatabaseTargetRepository targets;
    private final BackupExecutionRepository backups;
    private final Clock clock;

    public List<RetentionView> listAll() {
        Map<UUID, BackupRetentionPolicy> byTarget = policies.findAll().stream()
                .collect(Collectors.toMap(BackupRetentionPolicy::getTargetId, Function.identity()));
        return targets.findAll().stream()
                .map(target -> new RetentionView(target, Optional.ofNullable(byTarget.get(target.getId()))))
                .toList();
    }

    public DatabaseTarget getTarget(UUID targetId) {
        return targets.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("No database target with id " + targetId));
    }

    public Optional<BackupRetentionPolicy> findForTarget(UUID targetId) {
        getTarget(targetId);
        return policies.findByTargetId(targetId);
    }

    public BackupRetentionPolicy save(UUID targetId, SaveBackupRetentionPolicyCommand command) {
        getTarget(targetId);
        Instant now = clock.instant();
        BackupRetentionPolicy policy = policies.findByTargetId(targetId)
                .map(current -> current.edited(command.keepSuccessful(), now))
                .orElseGet(() -> BackupRetentionPolicy.create(targetId, command.keepSuccessful(), now));
        return policies.save(policy);
    }

    /**
     * The backups a policy keeping {@code keepSuccessful} would delete if it ran now, oldest first. Nothing is
     * deleted; the same selection as the next run, which also spares backups with restore history.
     */
    public List<BackupExecution> preview(UUID targetId, int keepSuccessful) {
        getTarget(targetId);
        return backups.findRetentionCandidates(targetId, keepSuccessful).stream()
                .sorted(java.util.Comparator.comparing(BackupExecution::getStartedAt))
                .toList();
    }

    /** Disabling retention never touches a backup. */
    public void disable(UUID targetId) {
        policies.deleteByTargetId(targetId);
    }
}
