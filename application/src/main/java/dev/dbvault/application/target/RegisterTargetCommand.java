package dev.dbvault.application.target;

import dev.dbvault.core.exception.InvalidTargetException;
import dev.dbvault.core.model.DatabaseEngine;
import java.util.UUID;

/**
 * What the operator typed, with the password still in plain text.
 *
 * <p>Short-lived by design: {@link ManageDatabaseTargetService} encrypts the
 * password on the way into the domain model and this record is then dropped.
 */
public record RegisterTargetCommand(
        String name,
        DatabaseEngine engine,
        String host,
        int port,
        String database,
        String username,
        String password,
        UUID storageProfileId) {

    /** A target whose backups stay on the built-in local storage. */
    public RegisterTargetCommand(String name, DatabaseEngine engine, String host, int port, String database,
            String username, String password) {
        this(name, engine, host, port, database, username, password, null);
    }

    public RegisterTargetCommand {
        if (engine == null) {
            throw new InvalidTargetException("engine", "Database engine is required");
        }
        // Every other field is checked by DatabaseTarget's constructor. The
        // password cannot be: by the time it reaches the model it is
        // ciphertext, and the ciphertext of an empty string is not empty.
        if (password == null || password.isBlank()) {
            throw new InvalidTargetException("password", "Password is required");
        }
    }
}
