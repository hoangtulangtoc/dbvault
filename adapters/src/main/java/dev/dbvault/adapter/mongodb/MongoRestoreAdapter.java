package dev.dbvault.adapter.mongodb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.dbvault.adapter.process.ClientBinaries;
import dev.dbvault.adapter.process.ProcessRunner;
import dev.dbvault.core.exception.RestoreFailedException;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.port.LogicalRestorePort;

/** Validates and restores one compressed MongoDB archive. */
@Component
class MongoRestoreAdapter implements LogicalRestorePort {

    private final ProcessRunner processRunner;
    private final Path binary;
    private final Duration timeout;

    MongoRestoreAdapter(
            ProcessRunner processRunner,
            @Value("${dbvault.mongodb.restore-path}") Path binary,
            @Value("${dbvault.restore.timeout}") Duration timeout) {
        this.processRunner = processRunner;
        this.binary = ClientBinaries.require(binary, "dbvault.mongodb.restore-path");
        this.timeout = timeout;
    }

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.MONGODB;
    }

    @Override
    public void restore(DatabaseConnection connection, String sourceDatabase, Path artifact) {
        if (!Files.isReadable(artifact)) {
            throw new RestoreFailedException(
                    "The backup artifact '%s' is missing or unreadable".formatted(artifact));
        }
        run(command(connection, sourceDatabase, artifact, true), connection,
                "The MongoDB archive is not readable");
        run(command(connection, sourceDatabase, artifact, false), connection, "mongorestore failed");
    }

    private void run(Command command, DatabaseConnection connection, String prefix) {
        ProcessRunner.Result result;
        try {
            result = MongoCredentialFile.use(connection.password(), config ->
                    processRunner.run(command.withConfig(config), Map.of(), timeout));
        } catch (ProcessRunner.ProcessFailedException | MongoCredentialFile.CredentialFileException e) {
            throw new RestoreFailedException(e.getMessage(), e);
        }
        if (!result.succeeded()) {
            throw new RestoreFailedException(
                    "%s (exit %d): %s".formatted(prefix, result.exitCode(), result.errorOutput()));
        }
    }

    Command command(DatabaseConnection connection, String sourceDatabase, Path artifact, boolean dryRun) {
        return new Command(connection, sourceDatabase, artifact, dryRun);
    }

    final class Command {
        private final DatabaseConnection connection;
        private final String sourceDatabase;
        private final Path artifact;
        private final boolean dryRun;

        private Command(
                DatabaseConnection connection, String sourceDatabase, Path artifact, boolean dryRun) {
            this.connection = connection;
            this.sourceDatabase = sourceDatabase;
            this.artifact = artifact;
            this.dryRun = dryRun;
        }

        List<String> withConfig(Path config) {
            List<String> command = new ArrayList<>(MongoCommand.connection(binary, connection, config));
            command.add("--archive=" + artifact);
            command.add("--gzip");
            command.add("--nsInclude=" + sourceDatabase + ".*");
            command.add("--nsFrom=" + sourceDatabase + ".*");
            command.add("--nsTo=" + connection.database() + ".*");
            if (dryRun) {
                command.add("--dryRun");
            } else {
                command.add("--drop");
                command.add("--stopOnError");
            }
            return List.copyOf(command);
        }
    }
}
