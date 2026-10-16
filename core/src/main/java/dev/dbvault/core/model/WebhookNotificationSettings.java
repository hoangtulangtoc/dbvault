package dev.dbvault.core.model;

public record WebhookNotificationSettings(String url) implements NotificationChannelSettings {
    @Override public NotificationChannelType type() { return NotificationChannelType.WEBHOOK; }
}
