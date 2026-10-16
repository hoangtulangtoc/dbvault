package dev.dbvault.core.model;

public enum NotificationChannelType {
    SLACK("Slack"),
    EMAIL("Email"),
    WEBHOOK("Webhook");

    private final String displayName;

    NotificationChannelType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
