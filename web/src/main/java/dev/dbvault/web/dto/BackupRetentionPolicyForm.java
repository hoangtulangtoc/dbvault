package dev.dbvault.web.dto;

import dev.dbvault.application.retention.SaveBackupRetentionPolicyCommand;
import dev.dbvault.core.model.BackupRetentionPolicy;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BackupRetentionPolicyForm {

    @NotNull(message = "{validation.keep.required}")
    @Min(value = 1, message = "{validation.keep.min}")
    private Integer keepSuccessful;

    public static BackupRetentionPolicyForm blank() {
        return new BackupRetentionPolicyForm();
    }

    public static BackupRetentionPolicyForm of(BackupRetentionPolicy policy) {
        BackupRetentionPolicyForm form = new BackupRetentionPolicyForm();
        form.keepSuccessful = policy.getKeepSuccessful();
        return form;
    }

    public SaveBackupRetentionPolicyCommand toCommand() {
        return new SaveBackupRetentionPolicyCommand(keepSuccessful);
    }
}
