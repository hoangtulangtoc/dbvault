package dev.dbvault.core.port;

import java.util.List;
import java.util.UUID;

import dev.dbvault.core.model.TargetNotificationSubscription;

public interface TargetNotificationSubscriptionRepository {
    List<TargetNotificationSubscription> findByTargetId(UUID targetId);
    long countForChannel(UUID channelId);
    void replaceForTarget(UUID targetId, List<TargetNotificationSubscription> subscriptions);
}
