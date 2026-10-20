package dev.dbvault.web.dto;

import java.util.UUID;

import dev.dbvault.application.target.RegisterTargetCommand;
import dev.dbvault.core.model.DatabaseEngine;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The registration form's backing bean.
 *
 * <p>A mutable bean because Spring's data binder needs setters, and the domain
 * model deliberately has none. The constraints here duplicate the model's own
 * checks on purpose: these produce per-field messages next to the input, while
 * the model's protect the invariant no matter which caller reaches it.
 */
@Data
public class DatabaseTargetForm {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = 100, message = "{validation.name.size}")
    private String name;

    @NotNull(message = "{validation.engine.required}")
    private DatabaseEngine engine;

    @NotBlank(message = "{validation.host.required}")
    @Size(max = 255, message = "{validation.host.size}")
    private String host;

    @NotNull(message = "{validation.port.required}")
    @Min(value = 1, message = "{validation.port.range}")
    @Max(value = 65535, message = "{validation.port.range}")
    private Integer port;

    @NotBlank(message = "{validation.database.required}")
    @Size(max = 64, message = "{validation.database.size}")
    private String database;

    @NotBlank(message = "{validation.username.required}")
    @Size(max = 63, message = "{validation.username.size}")
    private String username;

    @Size(max = 64, message = "{validation.authDatabase.size}")
    private String authenticationDatabase;

    @NotBlank(message = "{validation.password.required}")
    private String password;
    private UUID storageProfileId;
    private boolean verifyAfterBackup;
    /** Ticked on a new form: opting out of TLS is a choice the operator makes, not a default. */
    private boolean tlsRequired = true;

    public static DatabaseTargetForm blank() {
        DatabaseTargetForm form = new DatabaseTargetForm();
        form.setEngine(DatabaseEngine.MYSQL);
        form.setPort(DatabaseEngine.MYSQL.defaultPort());
        return form;
    }

    public RegisterTargetCommand toCommand() {
        return new RegisterTargetCommand(
                name, engine, host, port, database, username, password, authenticationDatabase,
                storageProfileId, verifyAfterBackup, tlsRequired);
    }
}
