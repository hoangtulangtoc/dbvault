package dev.dbvault.application.target;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.MysqlConnection;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.EncryptionPort;
import dev.dbvault.core.port.MysqlConnectionTestPort;

import lombok.RequiredArgsConstructor;

/**
 * Probes a registered target and remembers what happened.
 *
 * <p>This is where the stored password is decrypted, and the only place it
 * happens for this operation. The adapter is handed a {@link MysqlConnection}
 * holding plain text and never sees the key, so the decrypt → use → discard
 * cycle occurs once, here, where it can be reviewed.
 */
@Service
@RequiredArgsConstructor
public class TestTargetConnectionService {

    private final DatabaseTargetRepository repository;
    private final MysqlConnectionTestPort connectionTest;
    private final EncryptionPort encryption;
    private final Clock clock;

    /**
     * @throws NoSuchElementException if no target holds this id
     */
    public ConnectionCheck test(UUID targetId) {
        DatabaseTarget target = repository.findById(targetId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No database target with id " + targetId));

        MysqlConnectionTestPort.Result result = connectionTest.test(
                MysqlConnection.to(target, encryption.decrypt(target.getPasswordCiphertext())));

        ConnectionCheck check = result.successful()
                ? ConnectionCheck.passed(clock.instant())
                : ConnectionCheck.failed(result.message(), clock.instant());

        repository.recordConnectionCheck(targetId, check);
        return check;
    }
}
