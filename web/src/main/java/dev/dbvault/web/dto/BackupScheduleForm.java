package dev.dbvault.web.dto;

import java.util.UUID;

import dev.dbvault.application.schedule.SaveBackupScheduleCommand;
import dev.dbvault.core.model.BackupSchedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BackupScheduleForm {

    @NotBlank(message = "{validation.name.required}")
    @Size(max = BackupSchedule.MAX_NAME_LENGTH, message = "{validation.name.size}")
    private String name;

    @NotNull(message = "{validation.target.required}")
    private UUID targetId;

    @NotBlank(message = "{validation.cron.required}")
    @Size(max = BackupSchedule.MAX_CRON_LENGTH, message = "{validation.cron.size}")
    private String cronExpression;

    @NotBlank(message = "{validation.zone.required}")
    @Size(max = BackupSchedule.MAX_ZONE_LENGTH, message = "{validation.zone.size}")
    private String zoneId;

    private boolean enabled = true;

    public static BackupScheduleForm blank(UUID targetId) {
        BackupScheduleForm form = new BackupScheduleForm();
        form.targetId = targetId;
        form.cronExpression = "0 0 2 * * ?";
        form.zoneId = "UTC";
        form.enabled = true;
        return form;
    }

    public static BackupScheduleForm of(BackupSchedule schedule) {
        BackupScheduleForm form = new BackupScheduleForm();
        form.name = schedule.getName();
        form.targetId = schedule.getTargetId();
        form.cronExpression = schedule.getCronExpression();
        form.zoneId = schedule.getZoneId();
        form.enabled = schedule.isEnabled();
        return form;
    }

    public SaveBackupScheduleCommand toCommand() {
        return new SaveBackupScheduleCommand(name, targetId, cronExpression, zoneId, enabled);
    }
}
