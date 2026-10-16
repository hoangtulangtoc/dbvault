package dev.dbvault.adapter.mongodb;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.dbvault.core.model.DatabaseConnection;

final class MongoCommand {

    private MongoCommand() {
    }

    static List<String> connection(Path binary, DatabaseConnection connection, Path config) {
        List<String> command = new ArrayList<>(List.of(
                binary.toString(),
                "--host=" + connection.host(),
                "--port=" + connection.port(),
                "--username=" + connection.username(),
                "--authenticationDatabase=" + connection.authenticationDatabase(),
                "--config=" + config));
        // The database tools have no "prefer": without --tls they never encrypt, and with it they
        // also check the server's certificate against the image's CA store (ADR-041).
        if (connection.tlsRequired()) {
            command.add("--tls");
        }
        return command;
    }
}
