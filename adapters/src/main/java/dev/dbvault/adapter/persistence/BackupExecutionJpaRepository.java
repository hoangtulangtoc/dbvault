package dev.dbvault.adapter.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.dbvault.core.model.ExecutionStatus;

interface BackupExecutionJpaRepository extends JpaRepository<BackupExecutionEntity, UUID> {

    List<BackupExecutionEntity> findByStatus(ExecutionStatus status);

    boolean existsByTargetId(UUID targetId);
}
