package dev.dbvault.core.port;

import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;

/** Probes a target through the same client family used for backup. */
public interface ConnectionTestPort {

    DatabaseEngine engine();

    Result test(DatabaseConnection connection);

    record Result(boolean successful, String message) {
        public static Result ok() {
            return new Result(true, "");
        }

        public static Result failed(String message) {
            return new Result(false, message == null ? "" : message);
        }
    }
}
