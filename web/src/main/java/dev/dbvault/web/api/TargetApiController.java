package dev.dbvault.web.api;

import java.time.Instant;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.dbvault.application.target.ManageDatabaseTargetService;
import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.ExecutionStatus;
import dev.dbvault.core.port.BackupExecutionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only JSON for scripts and dbvault-cli: the targets, and the backups of one target. Version 1
 * of the API lives under {@code /api/v1} (ADR-039).
 *
 * <p>Nothing here is new behaviour: the same use cases the console reads, without the HTML. It
 * leaves out what a script has no business seeing — the stored password ciphertext and the
 * artifact's path on the server's disk.
 */
@RestController
@RequestMapping("/api/v1/targets")
@RequiredArgsConstructor
public class TargetApiController {

    private final ManageDatabaseTargetService targets;
    private final BackupExecutionRepository executions;

    @GetMapping
    List<TargetView> list() {
        return targets.listAll().stream().map(TargetView::of).toList();
    }

    /** {@code status}, in any case, keeps only the backups in that state; without it, every backup. */
    @GetMapping("/{id}/backups")
    List<BackupView> backups(@PathVariable UUID id, @RequestParam(required = false) String status) {
        ExecutionStatus only = status == null ? null : parse(status);
        if (targets.listAll().stream().noneMatch(target -> target.getId().equals(id))) {
            throw new NoSuchElementException("No target with id " + id);
        }
        return executions.findAllForTarget(id).stream()
                .filter(execution -> only == null || execution.getStatus() == only)
                .sorted(Comparator.comparing(BackupExecution::getStartedAt).reversed())
                .map(BackupView::of)
                .toList();
    }

    private static ExecutionStatus parse(String status) {
        try {
            return ExecutionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new UnknownStatusException("Unknown status '" + status + "'; use one of "
                    + String.join(", ", Arrays.stream(ExecutionStatus.values()).map(Enum::name).toList()));
        }
    }

    @ExceptionHandler(UnknownStatusException.class)
    ResponseEntity<Map<String, String>> badStatus(UnknownStatusException unknown) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", unknown.getMessage()));
    }

    private static final class UnknownStatusException extends RuntimeException {
        UnknownStatusException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing.getMessage()));
    }

    record TargetView(UUID id, String name, String engine, String host, int port, String database,
            String username, boolean tlsRequired, Instant createdAt) {
        static TargetView of(DatabaseTarget target) {
            return new TargetView(target.getId(), target.getName(), target.getEngine().name(), target.getHost(),
                    target.getPort(), target.getDatabaseName(), target.getUsername(), target.isTlsRequired(),
                    target.getCreatedAt());
        }
    }

    /** {@code sha256} is the checksum recorded when the dump was written, so a download can be verified. */
    record BackupView(UUID id, String status, Instant startedAt, Instant finishedAt, Long sizeBytes,
            String sha256, String error) {
        static BackupView of(BackupExecution execution) {
            return new BackupView(execution.getId(), execution.getStatus().name(), execution.getStartedAt(),
                    execution.getFinishedAt(), execution.getSizeBytes(), execution.getSha256(),
                    execution.getErrorMessage());
        }
    }
}
