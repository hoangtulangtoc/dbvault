package dev.dbvault.adapter.persistence;

import dev.dbvault.core.model.BackupExecution;

final class BackupExecutionMapper {

    private BackupExecutionMapper() {
    }

    static BackupExecutionEntity toEntity(BackupExecution execution) {
        BackupExecutionEntity entity = new BackupExecutionEntity();
        entity.setId(execution.getId());
        entity.setTargetId(execution.getTargetId());
        entity.setStatus(execution.getStatus());
        entity.setStartedAt(execution.getStartedAt());
        entity.setFinishedAt(execution.getFinishedAt());
        entity.setArtifactPath(execution.getArtifactPath());
        entity.setSizeBytes(execution.getSizeBytes());
        entity.setErrorMessage(execution.getErrorMessage());
        return entity;
    }

    static BackupExecution toDomain(BackupExecutionEntity entity) {
        return BackupExecution.builder()
                .id(entity.getId())
                .targetId(entity.getTargetId())
                .status(entity.getStatus())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .artifactPath(entity.getArtifactPath())
                .sizeBytes(entity.getSizeBytes())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
