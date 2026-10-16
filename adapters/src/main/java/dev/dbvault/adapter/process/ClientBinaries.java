package dev.dbvault.adapter.process;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks that a configured database client binary is actually runnable.
 *
 * <p>Called from adapter constructors so a wrong path stops the application at startup. dbvault can
 * do nothing useful without these binaries, and discovering that at the first backup — when someone
 * is relying on it — is the wrong time. Every engine's adapters share this one check.
 */
public final class ClientBinaries {

    private ClientBinaries() {
    }

    /**
     * @param property the configuration key the path came from, named in the error
     * @return {@code binary}, for use in a field initialiser
     */
    public static Path require(Path binary, String property) {
        if (!Files.isExecutable(binary)) {
            throw new IllegalStateException(
                    "%s points at '%s', which is not an executable file".formatted(property, binary));
        }
        return binary;
    }
}
