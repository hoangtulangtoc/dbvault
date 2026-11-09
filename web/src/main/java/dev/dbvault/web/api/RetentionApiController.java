package dev.dbvault.web.api;

import java.util.Comparator;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.dbvault.application.retention.ManageBackupRetentionService;
import dev.dbvault.core.model.BackupExecution;
import lombok.RequiredArgsConstructor;

/**
 * Which backups a retention policy would delete, for scripts and the CLI: the same selection as
 * the console's Preview and the next run, and like it, it deletes nothing and saves nothing.
 */
@RestController
@RequestMapping("/api/v1/targets/{id}/retention")
@RequiredArgsConstructor
public class RetentionApiController {

    private final ManageBackupRetentionService retention;

    /** Newest first, as the backups list is; {@code keep} is the number of successful backups to keep. */
    @GetMapping("/preview")
    List<TargetApiController.BackupView> preview(@PathVariable UUID id, @RequestParam int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException("keep must be 1 or more, not " + keep);
        }
        return retention.preview(id, keep).stream()
                .sorted(Comparator.comparing(BackupExecution::getStartedAt).reversed())
                .map(TargetApiController.BackupView::of)
                .toList();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badKeep(IllegalArgumentException invalid) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", invalid.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException missing) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", missing.getMessage()));
    }
}
