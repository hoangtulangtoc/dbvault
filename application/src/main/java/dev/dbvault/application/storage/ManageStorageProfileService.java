package dev.dbvault.application.storage;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.dbvault.core.exception.StorageProfileInUseException;
import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.StorageCredentialMode;
import dev.dbvault.core.model.StorageProfile;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.EncryptionPort;
import dev.dbvault.core.port.StorageProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManageStorageProfileService {
    private final StorageProfileRepository profiles;
    private final DatabaseTargetRepository targets;
    private final BackupExecutionRepository backups;
    private final ArtifactStorageService artifacts;
    private final EncryptionPort encryption;
    private final Clock clock;

    public List<StorageProfile> listAll() { return profiles.findAll(); }
    public StorageProfile get(UUID id) { return profiles.findById(id).orElseThrow(
            () -> new NoSuchElementException("No storage profile with id " + id)); }

    public StorageProfile create(SaveStorageProfileCommand command) {
        Instant now = clock.instant();
        return profiles.save(build(UUID.randomUUID(), now, now, null, command, null));
    }

    public StorageProfile edit(UUID id, SaveStorageProfileCommand command) {
        StorageProfile current = get(id);
        StorageProfile edited = build(id, current.getCreatedAt(), clock.instant(), null, command, current);
        if (backups.countForStorageProfile(id) > 0 && !current.sameLocation(edited)) {
            throw new IllegalStateException(
                    "Endpoint, region, bucket, prefix and path style cannot change after the profile is referenced");
        }
        return profiles.save(edited);
    }

    public ConnectionCheck test(UUID id) {
        StorageProfile profile = get(id);
        ConnectionCheck result;
        try {
            artifacts.test(profile);
            result = new ConnectionCheck(true, "Put, head, get and delete succeeded", clock.instant());
        } catch (RuntimeException e) {
            result = new ConnectionCheck(false, safeMessage(e), clock.instant());
        }
        profiles.recordConnectionCheck(id, result);
        return result;
    }

    public void delete(UUID id) {
        StorageProfile profile = profiles.findById(id).orElse(null);
        if (profile == null) return;
        if (isReferenced(id)) throw new StorageProfileInUseException(profile.getName());
        profiles.deleteById(id);
    }

    private StorageProfile build(UUID id, Instant createdAt, Instant updatedAt, ConnectionCheck check,
            SaveStorageProfileCommand command, StorageProfile current) {
        StorageCredentialMode mode = command.credentialMode();
        String accessKey = mode == StorageCredentialMode.STATIC ? command.accessKeyId() : null;
        String secretCiphertext = null;
        if (mode == StorageCredentialMode.STATIC) {
            if (command.suppliesSecret()) secretCiphertext = encryption.encrypt(command.secretAccessKey());
            else if (current != null && current.getCredentialMode() == StorageCredentialMode.STATIC)
                secretCiphertext = current.getSecretAccessKeyCiphertext();
        }
        return StorageProfile.builder().id(id).name(command.name()).endpoint(command.endpoint())
                .region(command.region()).bucket(command.bucket()).keyPrefix(command.keyPrefix())
                .pathStyle(command.pathStyle()).credentialMode(mode).accessKeyId(accessKey)
                .secretAccessKeyCiphertext(secretCiphertext).createdAt(createdAt).updatedAt(updatedAt)
                .lastConnectionCheck(check).build();
    }

    private boolean isReferenced(UUID id) {
        return targets.countForStorageProfile(id) > 0 || backups.countForStorageProfile(id) > 0;
    }

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "Storage connection test failed";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
