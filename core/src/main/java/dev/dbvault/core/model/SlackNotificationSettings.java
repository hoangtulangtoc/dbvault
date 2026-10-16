package dev.dbvault.core.model;

public record SlackNotificationSettings(String webhookUrl) implements NotificationChannelSettings {
    @Override public NotificationChannelType type() { return NotificationChannelType.SLACK; }
}
