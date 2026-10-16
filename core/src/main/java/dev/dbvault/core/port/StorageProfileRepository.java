package dev.dbvault.core.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.StorageProfile;

public interface StorageProfileRepository {
    StorageProfile save(StorageProfile profile);
    Optional<StorageProfile> findById(UUID id);
    List<StorageProfile> findAll();
    void recordConnectionCheck(UUID id, ConnectionCheck check);
    void deleteById(UUID id);
}
