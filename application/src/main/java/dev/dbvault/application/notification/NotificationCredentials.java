package dev.dbvault.application.notification;

import org.springframework.stereotype.Component;

import dev.dbvault.core.model.EmailNotificationSettings;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelSettings;
import dev.dbvault.core.model.SlackNotificationSettings;
import dev.dbvault.core.model.WebhookNotificationSettings;
import dev.dbvault.core.port.EncryptionPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationCredentials {
    private final EncryptionPort encryption;

    public NotificationChannelSettings decrypt(NotificationChannel channel) {
        return switch (channel.getType()) {
            case SLACK -> new SlackNotificationSettings(encryption.decrypt(channel.getWebhookUrlCiphertext()));
            case EMAIL -> new EmailNotificationSettings(channel.getEmailTo());
            case WEBHOOK -> new WebhookNotificationSettings(encryption.decrypt(channel.getWebhookUrlCiphertext()));
        };
    }

    String encrypt(String plainText) { return encryption.encrypt(plainText); }
}
