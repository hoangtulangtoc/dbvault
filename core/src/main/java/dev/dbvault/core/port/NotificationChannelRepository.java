package dev.dbvault.core.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.NotificationChannel;

public interface NotificationChannelRepository {
    NotificationChannel save(NotificationChannel channel);
    Optional<NotificationChannel> findById(UUID id);
    List<NotificationChannel> findAll();
    void recordConnectionCheck(UUID id, ConnectionCheck check);
    void deleteById(UUID id);
}
