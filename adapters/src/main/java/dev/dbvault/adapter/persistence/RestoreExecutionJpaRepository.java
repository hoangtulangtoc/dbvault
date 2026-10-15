package dev.dbvault.adapter.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.dbvault.core.model.ExecutionStatus;

interface RestoreExecutionJpaRepository extends JpaRepository<RestoreExecutionEntity, UUID> {

    List<RestoreExecutionEntity> findByStatus(ExecutionStatus status);
}
