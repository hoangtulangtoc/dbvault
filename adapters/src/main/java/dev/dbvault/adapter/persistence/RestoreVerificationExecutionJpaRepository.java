package dev.dbvault.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.dbvault.core.model.RestoreVerificationStatus;

interface RestoreVerificationExecutionJpaRepository
        extends JpaRepository<RestoreVerificationExecutionEntity, UUID> {
    List<RestoreVerificationExecutionEntity> findByBackupExecutionIdOrderByStartedAtDescIdDesc(UUID backupId);
    Optional<RestoreVerificationExecutionEntity> findFirstByBackupExecutionIdOrderByStartedAtDescIdDesc(UUID backupId);
    List<RestoreVerificationExecutionEntity> findByStatus(RestoreVerificationStatus status);
    boolean existsByBackupExecutionIdAndStatus(UUID backupId, RestoreVerificationStatus status);
}
