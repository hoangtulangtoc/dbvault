package dev.dbvault.web.dto;

import dev.dbvault.application.notification.SaveNotificationChannelCommand;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NotificationChannelForm {
    @NotBlank @Size(max = 100) private String name;
    @NotNull private NotificationChannelType type;
    private String webhookUrl;
    @Size(max = 255) private String emailTo;

    public static NotificationChannelForm blank(NotificationChannelType type) {
        NotificationChannelForm form = new NotificationChannelForm();
        form.setType(type);
        return form;
    }
    public static NotificationChannelForm of(NotificationChannel channel) {
        NotificationChannelForm form = blank(channel.getType());
        form.setName(channel.getName());
        form.setEmailTo(channel.getEmailTo());
        return form;
    }
    public SaveNotificationChannelCommand toCommand() {
        return new SaveNotificationChannelCommand(name, type, webhookUrl, emailTo);
    }
    public void clearSecrets() { webhookUrl = null; }
}
