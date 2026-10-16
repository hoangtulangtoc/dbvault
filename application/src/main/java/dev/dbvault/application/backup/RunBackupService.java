package dev.dbvault.application.backup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import dev.dbvault.application.EngineAdapterRegistry;
import dev.dbvault.application.notification.NotificationDispatcher;
import dev.dbvault.application.retention.ApplyBackupRetentionService;
import dev.dbvault.application.storage.ArtifactStorageService;
import dev.dbvault.application.storage.ArtifactStorageService.PreparedWrite;
import dev.dbvault.core.exception.BackupFailedException;
import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.NotificationEventType;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.EncryptionPort;
import dev.dbvault.core.port.LogicalBackupPort;
import dev.dbvault.core.model.ArtifactReference;


/**
 * Starts backups and runs them.
 *
 * <p>The two halves are deliberately separate. {@link #start} validates,
 * persists a RUNNING row and returns — so an accepted backup has a stable URL
 * before any work begins, and a crash leaves an inspectable row rather than a
 * lost in-memory task. {@link #run} is what the background thread executes.
 */
@Service
public class RunBackupService {

    private static final Logger log = LoggerFactory.getLogger(RunBackupService.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private final DatabaseTargetRepository targets;
    private final BackupExecutionRepository executions;
    private final EngineAdapterRegistry adapters;
    private final ArtifactStorageService storage;
    private final EncryptionPort encryption;
    private final Executor jobExecutor;
    private final Clock clock;
    private final ApplyBackupRetentionService retention;
    private final NotificationDispatcher notifications;

    @Autowired
    public RunBackupService(DatabaseTargetRepository targets, BackupExecutionRepository executions,
            EngineAdapterRegistry adapters, ArtifactStorageService storage, EncryptionPort encryption,
            Executor jobExecutor, Clock clock, ApplyBackupRetentionService retention,
            NotificationDispatcher notifications) {
        this.targets = targets; this.executions = executions; this.adapters = adapters; this.storage = storage;
        this.encryption = encryption; this.jobExecutor = jobExecutor; this.clock = clock; this.retention = retention;
        this.notifications = notifications;
    }

    public RunBackupService(DatabaseTargetRepository targets, BackupExecutionRepository executions,
            EngineAdapterRegistry adapters, ArtifactStorageService storage, EncryptionPort encryption,
            Executor jobExecutor, Clock clock, ApplyBackupRetentionService retention) {
        this(targets, executions, adapters, storage, encryption, jobExecutor, clock, retention, null);
    }

    public RunBackupService(DatabaseTargetRepository targets, BackupExecutionRepository executions,
            EngineAdapterRegistry adapters, dev.dbvault.core.port.StoragePort storage,
            EncryptionPort encryption, Executor jobExecutor, Clock clock, ApplyBackupRetentionService retention) {
        this(targets, executions, adapters, ArtifactStorageService.localOnly(storage), encryption,
                jobExecutor, clock, retention, null);
    }

    /**
     * Accepts a backup and hands it to the background.
     *
     * <p><strong>Not {@code @Transactional}, and that is the point.</strong> The
     * RUNNING row must be committed before the background thread can load it;
     * inside a transaction the work would be submitted against a row no other
     * connection can see yet.
     *
     * @return the id of the persisted execution, ready to be polled
     * @throws NoSuchElementException if no target holds this id
     */
    public UUID start(UUID targetId) {
        DatabaseTarget target = targets.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException("No database target with id " + targetId));

        BackupExecution execution = executions.save(
                BackupExecution.started(UUID.randomUUID(), targetId, target.getStorageProfileId(), clock.instant()));

        try {
            jobExecutor.execute(() -> run(execution.getId(), target));
        } catch (RejectedExecutionException e) {
            // The queue is full. Saying so beats a row that sits at RUNNING
            // forever because nothing ever picked it up.
            notify(target, finish(execution, "Too many jobs are already running or queued. Try again shortly."),
                    NotificationEventType.BACKUP_FAILED);
        }
        return execution.getId();
    }

    /** Runs one accepted backup. Called on a background thread; never throws. */
    void run(UUID executionId, DatabaseTarget target) {
        BackupExecution execution = executions.findById(executionId).orElseThrow();
        notify(target, execution, NotificationEventType.BACKUP_STARTED);
        LogicalBackupPort backupEngine = adapters.backupFor(target.getEngine());
        String filename = artifactFileName(target, execution.getStartedAt(), backupEngine.artifactSuffix());
        ArtifactReference published = null;
        try (PreparedWrite destination = storage.prepareWrite(
                execution.getStorageProfileId(), target.getId(), executionId, filename)) {
            // A BackupFailedException, not a generic one: for local storage
            // that path does not clean up, and the file found here is not ours.
            if (storage.occupied(destination)) {
                throw new BackupFailedException(
                        "Refusing to overwrite an existing artifact: " + destination.path());
            }
            // Decrypted here, at the last moment and on the thread that uses
            // it, rather than being carried through the queue.
            DatabaseConnection connection =
                    DatabaseConnection.to(target, encryption.decryptFor(target.getId(), target.getPasswordCiphertext()));

            long reportedSize = backupEngine.dumpTo(connection, destination.path());
            // Read back from disk once the engine has closed the file, so the
            // checksum describes what was stored rather than what was sent.
            long sizeBytes = storage.size(destination.path(), destination.remote(), reportedSize);
            String sha256 = storage.sha256(destination.path(), destination.remote());
            published = storage.publish(destination);
            BackupExecution succeeded = executions.save(
                    execution.succeeded(published.locator(), sizeBytes, sha256, clock.instant()));
            notify(target, succeeded, NotificationEventType.BACKUP_SUCCESS);
            log.info("Backup {} of target {} succeeded: {} ({} bytes, sha256 {})",
                    executionId, target.getName(), published.locator(), sizeBytes, sha256);

        } catch (BackupFailedException e) {
            if (execution.getStorageProfileId() != null) {
                cleanupFailedArtifact(execution, published, filename);
            }
            notify(target, finish(execution, e.getMessage()), NotificationEventType.BACKUP_FAILED);
            return;
        } catch (RuntimeException e) {
            // Anything unexpected still has to land on the row, or the console
            // shows RUNNING for a job that has already died.
            log.error("Backup {} of target {} failed unexpectedly", executionId, target.getName(), e);
            cleanupFailedArtifact(execution, published, filename);
            notify(target, finish(execution, e.toString()), NotificationEventType.BACKUP_FAILED);
            return;
        }

        // Retention begins only after the successful row is durable. It has a
        // separate failure boundary: cleanup can fail, but the backup did not.
        try {
            retention.applyAfterSuccessfulBackup(target.getId());
        } catch (RuntimeException e) {
            log.error("Could not apply automatic retention after backup {}", executionId, e);
        }
    }

    /**
     * Marks every execution left RUNNING by a previous process as failed.
     *
     * <p>Backups run in this process and nowhere else, so a RUNNING row at
     * startup cannot still be running. Without this the console would show it
     * as in progress forever.
     */
    public int failInterruptedBackups() {
        List<BackupExecution> stranded = executions.findRunning();
        stranded.forEach(execution -> {
            BackupExecution failed = finish(
                    execution, "Interrupted: the application stopped while this backup was running.");
            notifyIfTargetExists(failed, NotificationEventType.BACKUP_FAILED);
        });
        if (!stranded.isEmpty()) {
            log.warn("Marked {} backup(s) left running by a previous process as failed", stranded.size());
        }
        return stranded.size();
    }

    private BackupExecution finish(BackupExecution execution, String message) {
        return executions.save(execution.failed(message, clock.instant()));
    }

    private void notifyIfTargetExists(BackupExecution execution, NotificationEventType event) {
        targets.findById(execution.getTargetId()).ifPresent(target -> notify(target, execution, event));
    }

    private void notify(DatabaseTarget target, BackupExecution execution, NotificationEventType event) {
        if (notifications != null) notifications.publishBackup(target, execution, event);
    }

    private void cleanupFailedArtifact(BackupExecution execution, ArtifactReference published, String filename) {
        try {
            if (published != null) {
                storage.delete(published);
            } else {
                try (PreparedWrite planned = storage.prepareWrite(
                        execution.getStorageProfileId(), execution.getTargetId(), execution.getId(), filename)) {
                    storage.delete(planned.reference());
                }
            }
        } catch (RuntimeException cleanupFailure) {
            log.warn("Could not clean up artifact for failed backup {}", execution.getId(), cleanupFailure);
        }
    }

    /**
     * {@code shop_20260909_075300.sql.gz}. The database name is sanitised because
     * it comes from user input and is about to become a file name.
     */
    static String artifactFileName(DatabaseTarget target, Instant startedAt, String suffix) {
        if (suffix == null || !suffix.matches("\\.[a-zA-Z0-9.]+")) {
            throw new IllegalArgumentException(
                    "Artifact suffix must begin with a dot and contain only letters, digits or dots");
        }
        String schema = target.getDatabaseName().replaceAll("[^a-zA-Z0-9._-]", "_");
        return "%s_%s%s".formatted(schema, TIMESTAMP.format(startedAt), suffix);
    }
}
