package dev.dbvault.adapter.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DatabaseTargetJpaRepository extends JpaRepository<DatabaseTargetEntity, UUID> {
}
