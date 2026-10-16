package dev.dbvault.core.model;

/** Ready-to-send settings. Secret values in these records must never be persisted or logged. */
public sealed interface NotificationChannelSettings
        permits SlackNotificationSettings, EmailNotificationSettings, WebhookNotificationSettings {
    NotificationChannelType type();
}
