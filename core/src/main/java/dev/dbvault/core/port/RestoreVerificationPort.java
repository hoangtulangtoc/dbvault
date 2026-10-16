package dev.dbvault.core.port;

import java.nio.file.Path;
import java.util.UUID;

import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.RestoreVerificationResult;

/** Restores one logical artifact into an isolated database and validates it. */
public interface RestoreVerificationPort {

    DatabaseEngine engine();

    boolean isAvailable();

    RestoreVerificationResult verify(
            UUID verificationId, DatabaseTarget sourceTarget, Path artifact);

    /** Best-effort cleanup for work left behind by a previous application process. */
    default void abortInterrupted(UUID verificationId) {
        // File-only and local-process adapters have nothing durable to stop.
    }
}
