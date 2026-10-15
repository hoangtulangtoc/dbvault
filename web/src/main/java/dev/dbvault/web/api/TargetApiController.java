package dev.dbvault.web.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.dbvault.application.target.ManageDatabaseTargetService;
import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.BackupExecutionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only JSON for scripts and dbvault-cli: the targets, and the backups of one target.
 *
 * <p>Nothing here is new behaviour: the same use cases the console reads, without the HTML. It
 * leaves out what a script has no business seeing — the stored password ciphertext and the
 * artifact's path on the server's disk.
 */
@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class TargetApiController {

    private final ManageDatabaseTargetService targets;
    private final BackupExecutionRepository executions;

    @GetMapping
    List<TargetView> list() {
        return targets.listAll().stream().map(TargetView::of).toList();
    }

    @GetMapping("/{id}/backups")
    List<BackupView> backups(@PathVariable UUID id) {
        if (targets.listAll().stream().noneMatch(target -> target.getId().equals(id))) {
            throw new NoSuchElementException("No target with id " + id);
        }
        return executions.findAllNewestFirst().stream()
                .filter(execution -> execution.getTargetId().equals(id))
                .map(BackupView::of)
                .toList();
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing.getMessage()));
    }

    record TargetView(UUID id, String name, String host, int port, String database, String username,
            Instant createdAt) {
        static TargetView of(DatabaseTarget target) {
            return new TargetView(target.getId(), target.getName(), target.getHost(), target.getPort(),
                    target.getDatabaseName(), target.getUsername(), target.getCreatedAt());
        }
    }

    record BackupView(UUID id, String status, Instant startedAt, Instant finishedAt, Long sizeBytes,
            String error) {
        static BackupView of(BackupExecution execution) {
            return new BackupView(execution.getId(), execution.getStatus().name(), execution.getStartedAt(),
                    execution.getFinishedAt(), execution.getSizeBytes(), execution.getErrorMessage());
        }
    }
}
