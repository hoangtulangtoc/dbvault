package dev.dbvault.core.port;

import java.nio.file.Path;

import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;

/** Restores one full logical backup into a target of the same engine. */
public interface LogicalRestorePort {

    DatabaseEngine engine();

    void restore(DatabaseConnection connection, Path artifact);
}
