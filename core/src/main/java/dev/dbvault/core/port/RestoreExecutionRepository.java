package dev.dbvault.core.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.dbvault.core.model.RestoreExecution;

public interface RestoreExecutionRepository {

    RestoreExecution save(RestoreExecution execution);

    Optional<RestoreExecution> findById(UUID id);

    /** Most recent first. The order is part of the contract. */
    List<RestoreExecution> findAllNewestFirst();

    /** @see BackupExecutionRepository#findRunning() */
    List<RestoreExecution> findRunning();
}
