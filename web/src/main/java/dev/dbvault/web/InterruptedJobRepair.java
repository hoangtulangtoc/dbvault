package dev.dbvault.web;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.dbvault.application.backup.RunBackupService;
import dev.dbvault.application.restore.RestoreBackupService;
import dev.dbvault.application.verification.RestoreVerificationService;

import lombok.RequiredArgsConstructor;

/**
 * Repairs backups and restores left RUNNING by a process that is no longer here.
 *
 * <p>Separate from {@link JobExecutorConfig} on purpose: that class defines the
 * executor these services depend on, so a configuration holding both would be a
 * bean cycle. That cycle is exactly what {@code DbvaultApplicationIT} exists
 * to catch.
 */
@Component
@RequiredArgsConstructor
class InterruptedJobRepair {

    private final RunBackupService runBackupService;
    private final RestoreBackupService restoreBackupService;
    private final RestoreVerificationService restoreVerificationService;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    void repairInterruptedJobs() {
        runBackupService.failInterruptedBackups();
        restoreBackupService.failInterruptedRestores();
        restoreVerificationService.failInterruptedVerifications();
    }
}
