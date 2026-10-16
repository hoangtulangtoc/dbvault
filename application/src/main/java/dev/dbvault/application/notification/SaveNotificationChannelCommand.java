package dev.dbvault.application.notification;

import dev.dbvault.core.model.NotificationChannelType;

public record SaveNotificationChannelCommand(
        String name,
        NotificationChannelType type,
        String webhookUrl,
        String emailTo) {
    public boolean suppliesWebhookUrl() { return webhookUrl != null && !webhookUrl.isBlank(); }
}
