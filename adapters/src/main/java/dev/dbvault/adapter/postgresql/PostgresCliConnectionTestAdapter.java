package dev.dbvault.adapter.postgresql;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.dbvault.adapter.process.ClientBinaries;
import dev.dbvault.adapter.process.ProcessRunner;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.port.ConnectionTestPort;

/** Tests PostgreSQL through psql, the same client family the backup path uses. */
@Component
class PostgresCliConnectionTestAdapter implements ConnectionTestPort {

    private final ProcessRunner processRunner;
    private final Path binary;
    private final Duration connectTimeout;

    PostgresCliConnectionTestAdapter(
            ProcessRunner processRunner,
            @Value("${dbvault.postgresql.psql-path}") Path binary,
            @Value("${dbvault.postgresql.connect-timeout}") Duration connectTimeout) {
        this.processRunner = processRunner;
        this.binary = ClientBinaries.require(binary, "dbvault.postgresql.psql-path");
        this.connectTimeout = connectTimeout;
    }

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.POSTGRESQL;
    }

    @Override
    public Result test(DatabaseConnection connection) {
        try {
            ProcessRunner.Result result = processRunner.run(
                    List.of(
                            binary.toString(),
                            "--host=" + connection.host(),
                            "--port=" + connection.port(),
                            "--username=" + connection.username(),
                            "--dbname=" + connection.database(),
                            "--no-password",
                            "--tuples-only",
                            "--no-align",
                            "--command=SELECT 1"),
                    environment(connection, connectTimeout),
                    connectTimeout.plusSeconds(5));
            return result.succeeded() ? Result.ok() : Result.failed(result.errorOutput());
        } catch (ProcessRunner.ProcessFailedException e) {
            return Result.failed(e.getMessage());
        }
    }

    /**
     * What every PostgreSQL client gets. {@code PGSSLMODE=require} refuses a server that will not
     * encrypt; libpq's default {@code prefer} quietly falls back to plain text, so an opted-out
     * target says {@code prefer} explicitly rather than inheriting whatever the host set (ADR-041).
     */
    static Map<String, String> environment(DatabaseConnection connection, Duration connectTimeout) {
        return Map.of(
                "PGPASSWORD", connection.password(),
                "PGCONNECT_TIMEOUT", Long.toString(connectTimeout.toSeconds()),
                "PGSSLMODE", connection.tlsRequired() ? "require" : "prefer");
    }
}
