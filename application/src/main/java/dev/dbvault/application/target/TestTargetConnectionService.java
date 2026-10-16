package dev.dbvault.application.target;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.dbvault.application.EngineAdapterRegistry;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.ConnectionTestPort;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.EncryptionPort;

import lombok.RequiredArgsConstructor;

/**
 * Probes a registered target and remembers what happened.
 *
 * <p>This is where the stored password is decrypted, and the only place it
 * happens for this operation. The adapter is handed a {@link DatabaseConnection}
 * holding plain text and never sees the key, so the decrypt → use → discard
 * cycle occurs once, here, where it can be reviewed.
 */
@Service
@RequiredArgsConstructor
public class TestTargetConnectionService {

    private final DatabaseTargetRepository repository;
    private final EngineAdapterRegistry adapters;
    private final EncryptionPort encryption;
    private final Clock clock;

    /**
     * @throws NoSuchElementException if no target holds this id
     */
    public ConnectionCheck test(UUID targetId) {
        DatabaseTarget target = repository.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No database target with id " + targetId));

        ConnectionTestPort.Result result = adapters.connectionTestFor(target.getEngine()).test(
                DatabaseConnection.to(target, encryption.decryptFor(target.getId(), target.getPasswordCiphertext())));

        ConnectionCheck check = result.successful()
                ? ConnectionCheck.passed(clock.instant())
                : ConnectionCheck.failed(result.message(), clock.instant());

        repository.recordConnectionCheck(targetId, check);
        return check;
    }
}
