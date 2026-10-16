package dev.dbvault.core.port;

import dev.dbvault.core.model.NotificationChannelSettings;
import dev.dbvault.core.model.NotificationChannelType;
import dev.dbvault.core.model.NotificationMessage;

public interface NotificationPort {
    NotificationChannelType supportedType();
    void send(NotificationChannelSettings settings, NotificationMessage message);
}
