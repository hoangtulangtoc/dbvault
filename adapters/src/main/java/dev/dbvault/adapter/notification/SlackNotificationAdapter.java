package dev.dbvault.adapter.notification;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelSettings;
import dev.dbvault.core.model.NotificationChannelType;
import dev.dbvault.core.model.NotificationMessage;
import dev.dbvault.core.model.SlackNotificationSettings;
import dev.dbvault.core.port.NotificationPort;

@Component
public class SlackNotificationAdapter implements NotificationPort {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpNotificationSupport http;
    public SlackNotificationAdapter() { this(new HttpNotificationSupport()); }
    SlackNotificationAdapter(HttpNotificationSupport http) { this.http = http; }
    @Override public NotificationChannelType supportedType() { return NotificationChannelType.SLACK; }
    @Override public void send(NotificationChannelSettings settings, NotificationMessage message) {
        if (!(settings instanceof SlackNotificationSettings slack)) {
            throw new IllegalArgumentException("Slack settings required");
        }
        NotificationChannel.validateWebhookUrl(slack.webhookUrl(), true);
        try {
            http.postJson(slack.webhookUrl(),
                    objectMapper.writeValueAsString(java.util.Map.of("text", message.text())), "Slack");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Slack notification payload failed", e);
        }
    }
}
