package dev.dbvault.adapter.mongodb;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.dbvault.adapter.process.ClientBinaries;
import dev.dbvault.adapter.process.ProcessRunner;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.port.ConnectionTestPort;

/** Probes MongoDB through mongodump without reading an operator collection. */
@Component
class MongoCliConnectionTestAdapter implements ConnectionTestPort {

    private final ProcessRunner processRunner;
    private final Path binary;
    private final Duration timeout;

    MongoCliConnectionTestAdapter(
            ProcessRunner processRunner,
            @Value("${dbvault.mongodb.dump-path}") Path binary,
            @Value("${dbvault.mongodb.connect-timeout}") Duration timeout) {
        this.processRunner = processRunner;
        this.binary = ClientBinaries.require(binary, "dbvault.mongodb.dump-path");
        this.timeout = timeout;
    }

    @Override
    public DatabaseEngine engine() {
        return DatabaseEngine.MONGODB;
    }

    @Override
    public Result test(DatabaseConnection connection) {
        try {
            ProcessRunner.Result result = MongoCredentialFile.use(connection.password(), config ->
                    processRunner.run(command(connection, config), Map.of(), timeout));
            return result.succeeded() ? Result.ok() : Result.failed(result.errorOutput());
        } catch (ProcessRunner.ProcessFailedException | MongoCredentialFile.CredentialFileException e) {
            return Result.failed(e.getMessage());
        }
    }

    List<String> command(DatabaseConnection connection, Path config) {
        List<String> command = new ArrayList<>(MongoCommand.connection(binary, connection, config));
        command.add("--db=" + connection.database());
        command.add("--collection=__dbvault_connection_probe_"
                + UUID.randomUUID().toString().replace("-", ""));
        // No value means stdout. An absent collection produces only a small
        // archive header while still exercising connection and authentication.
        command.add("--archive");
        return List.copyOf(command);
    }
}
