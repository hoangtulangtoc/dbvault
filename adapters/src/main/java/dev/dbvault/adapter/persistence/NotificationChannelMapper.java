package dev.dbvault.adapter.persistence;

import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.NotificationChannel;

final class NotificationChannelMapper {
    private NotificationChannelMapper() {}

    static NotificationChannelEntity toEntity(NotificationChannel channel) {
        NotificationChannelEntity entity = new NotificationChannelEntity();
        entity.setId(channel.getId());
        entity.setName(channel.getName());
        entity.setType(channel.getType());
        entity.setWebhookUrlEnc(channel.getWebhookUrlCiphertext());
        entity.setEmailTo(channel.getEmailTo());
        entity.setCreatedAt(channel.getCreatedAt());
        entity.setUpdatedAt(channel.getUpdatedAt());
        ConnectionCheck check = channel.getLastConnectionCheck();
        if (check != null) {
            entity.setLastConnectionSuccessful(check.successful());
            entity.setLastConnectionMessage(check.message());
            entity.setLastConnectionCheckedAt(check.checkedAt());
        }
        return entity;
    }

    static NotificationChannel toDomain(NotificationChannelEntity entity) {
        ConnectionCheck check = entity.getLastConnectionSuccessful() == null
                || entity.getLastConnectionCheckedAt() == null ? null : new ConnectionCheck(
                        entity.getLastConnectionSuccessful(), entity.getLastConnectionMessage(),
                        entity.getLastConnectionCheckedAt());
        return NotificationChannel.builder()
                .id(entity.getId()).name(entity.getName()).type(entity.getType())
                .webhookUrlCiphertext(entity.getWebhookUrlEnc()).emailTo(entity.getEmailTo())
                .createdAt(entity.getCreatedAt()).updatedAt(entity.getUpdatedAt())
                .lastConnectionCheck(check).build();
    }
}
