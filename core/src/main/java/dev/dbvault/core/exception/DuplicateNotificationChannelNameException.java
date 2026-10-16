package dev.dbvault.core.exception;

public final class DuplicateNotificationChannelNameException extends RuntimeException {
    public DuplicateNotificationChannelNameException(String name) {
        super("A notification channel named '" + name + "' already exists");
    }
}
