package dev.dbvault.application.target;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.EncryptionPort;

import lombok.RequiredArgsConstructor;

/**
 * Registering, listing and removing backup targets.
 *
 * <p>A concrete class, not an interface with one implementation: the web layer
 * calls it directly. The ports it depends on are interfaces because they sit
 * at the edge of the hexagon and keep this class testable without a database;
 * an interface in front of <em>this</em> class would buy nothing.
 *
 * <p>This is also the only type in the system that calls {@link EncryptionPort}.
 */
@Service
@RequiredArgsConstructor
public class ManageDatabaseTargetService {

    private final DatabaseTargetRepository repository;
    private final EncryptionPort encryption;
    private final Clock clock;

    /**
     * @throws dev.dbvault.core.exception.InvalidTargetException
     *         if a field is missing or out of range
     * @throws dev.dbvault.core.exception.DuplicateTargetNameException
     *         if the name is taken
     */
    public DatabaseTarget register(RegisterTargetCommand command) {
        DatabaseTarget target = DatabaseTarget.builder()
                .id(UUID.randomUUID())
                .name(command.name())
                .host(command.host())
                .port(command.port())
                .databaseName(command.database())
                .username(command.username())
                .passwordCiphertext(encryption.encrypt(command.password()))
                .createdAt(clock.instant())
                .build();

        return repository.save(target);
    }

    public List<DatabaseTarget> listAll() {
        return repository.findAll();
    }

    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
