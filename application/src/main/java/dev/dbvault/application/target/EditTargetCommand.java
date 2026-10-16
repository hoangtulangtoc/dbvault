package dev.dbvault.application.target;

import java.util.UUID;

/**
 * New connection details for an existing target, with the password — if there
 * is one — still in plain text.
 *
 * <p>No schema: a target's schema is fixed when it is registered. See
 * {@link dev.dbvault.core.model.DatabaseTarget#edited}.
 *
 * @param password the new password, or {@code null} or blank to keep the
 *        current one. Blank means "unchanged" rather than "empty" because a
 *        password field is never pre-filled: an operator who only renames a
 *        target leaves it empty.
 */
public record EditTargetCommand(
        String name,
        String host,
        int port,
        String username,
        String password,
        String authenticationDatabase,
        UUID storageProfileId,
        boolean verifyAfterBackup,
        boolean tlsRequired) {

    /** An edit after which the target's connections must be encrypted, which is the default. */
    public EditTargetCommand(String name, String host, int port, String username, String password,
            String authenticationDatabase, UUID storageProfileId, boolean verifyAfterBackup) {
        this(name, host, port, username, password, authenticationDatabase, storageProfileId, verifyAfterBackup, true);
    }

    /** An edit that leaves the storage destination where it is. */
    public EditTargetCommand(String name, String host, int port, String username, String password,
            String authenticationDatabase) {
        this(name, host, port, username, password, authenticationDatabase, null, false, true);
    }

    /** An edit of a SQL target that leaves the storage destination where it is. */
    public EditTargetCommand(String name, String host, int port, String username, String password) {
        this(name, host, port, username, password, null, null, false, true);
    }

    /** An edit with automatic restore verification off. */
    public EditTargetCommand(String name, String host, int port, String username, String password,
            String authenticationDatabase, UUID storageProfileId) {
        this(name, host, port, username, password, authenticationDatabase, storageProfileId, false, true);
    }

    public boolean changesPassword() {
        return password != null && !password.isBlank();
    }
}
