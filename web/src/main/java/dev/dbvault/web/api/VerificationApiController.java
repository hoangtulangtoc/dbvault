package dev.dbvault.web.api;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.dbvault.application.verification.RestoreVerificationService;
import dev.dbvault.core.model.RestoreVerificationExecution;
import dev.dbvault.core.port.RestoreVerificationExecutionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Restore verification for scripts and dbvault-cli: start a test restore of one backup, then follow
 * it until it has an outcome. The same use case as the console's Test restore (ADR-029).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VerificationApiController {

    private final RestoreVerificationService verification;
    private final RestoreVerificationExecutionRepository verifications;

    /** {@code 202} with the attempt as it stands; its {@code Location} is where to follow it. */
    @PostMapping("/backups/{id}/verifications")
    ResponseEntity<VerificationView> start(@PathVariable UUID id) {
        UUID started = verification.start(id);
        VerificationView view = verifications.findById(started).map(VerificationView::of).orElseThrow();
        return ResponseEntity.accepted().location(URI.create("/api/v1/verifications/" + started)).body(view);
    }

    @GetMapping("/verifications/{id}")
    VerificationView get(@PathVariable UUID id) {
        return verifications.findById(id).map(VerificationView::of)
                .orElseThrow(() -> new NoSuchElementException("No restore verification with id " + id));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing.getMessage()));
    }

    /** Disabled, unsupported, unavailable, or a backup that did not succeed: nothing was started. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> refused(IllegalStateException refused) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", refused.getMessage()));
    }

    /** {@code status} is RUNNING, SUCCEEDED or FAILED; only a finished attempt has the rest. */
    record VerificationView(UUID id, UUID backupId, String status, Instant startedAt, Instant finishedAt,
            Integer checkedObjects, String summary, String error) {
        static VerificationView of(RestoreVerificationExecution execution) {
            return new VerificationView(execution.getId(), execution.getBackupExecutionId(),
                    execution.getStatus().name(), execution.getStartedAt(), execution.getFinishedAt(),
                    execution.getCheckedObjects(), execution.getResultSummary(), execution.getErrorMessage());
        }
    }
}
