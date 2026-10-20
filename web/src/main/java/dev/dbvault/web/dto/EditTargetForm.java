package dev.dbvault.web.dto;

import java.util.UUID;

import dev.dbvault.application.target.EditTargetCommand;
import dev.dbvault.core.model.DatabaseTarget;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The edit form's backing bean.
 *
 * <p>Not {@link DatabaseTargetForm} with a flag: two fields behave differently
 * here. There is no schema, because a target's schema is fixed once it is
 * registered, and the password may be left empty to keep the stored one — it
 * is never sent to the browser, so the field always starts out blank.
 */
@Data
public class EditTargetForm {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = 100, message = "{validation.name.size}")
    private String name;

    @NotBlank(message = "{validation.host.required}")
    @Size(max = 255, message = "{validation.host.size}")
    private String host;

    @NotNull(message = "{validation.port.required}")
    @Min(value = 1, message = "{validation.port.range}")
    @Max(value = 65535, message = "{validation.port.range}")
    private Integer port;

    @NotBlank(message = "{validation.username.required}")
    @Size(max = 63, message = "{validation.username.size}")
    private String username;

    @Size(max = 64, message = "{validation.authDatabase.size}")
    private String authenticationDatabase;

    /** Blank keeps the stored password. */
    private String password;
    private UUID storageProfileId;
    private boolean verifyAfterBackup;
    private boolean tlsRequired = true;

    /** Pre-filled from the target, all but the password. */
    public static EditTargetForm of(DatabaseTarget target) {
        EditTargetForm form = new EditTargetForm();
        form.setName(target.getName());
        form.setHost(target.getHost());
        form.setPort(target.getPort());
        form.setUsername(target.getUsername());
        form.setAuthenticationDatabase(target.getAuthenticationDatabase());
        form.setStorageProfileId(target.getStorageProfileId());
        form.setVerifyAfterBackup(target.isVerifyAfterBackup());
        form.setTlsRequired(target.isTlsRequired());
        return form;
    }

    public EditTargetCommand toCommand() {
        return new EditTargetCommand(
                name, host, port, username, password, authenticationDatabase, storageProfileId, verifyAfterBackup,
                tlsRequired);
    }
}
