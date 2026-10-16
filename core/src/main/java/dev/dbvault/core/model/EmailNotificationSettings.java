package dev.dbvault.core.model;

public record EmailNotificationSettings(String recipient) implements NotificationChannelSettings {
    @Override public NotificationChannelType type() { return NotificationChannelType.EMAIL; }
}
