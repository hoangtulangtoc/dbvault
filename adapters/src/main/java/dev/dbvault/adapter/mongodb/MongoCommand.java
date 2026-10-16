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
        return command;
    }
}
