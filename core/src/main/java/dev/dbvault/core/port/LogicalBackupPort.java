package dev.dbvault.core.port;

import java.nio.file.Path;

import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;

/** Produces one full logical backup artifact. */
public interface LogicalBackupPort {

    DatabaseEngine engine();

    /** Includes its leading dot, for example {@code .sql.gz} or {@code .dump}. */
    String artifactSuffix();

    /** Writes the complete artifact and removes partial output on failure. */
    long dumpTo(DatabaseConnection connection, Path destination);
}
