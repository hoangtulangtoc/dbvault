package dev.dbvault.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NotificationChannelTest {
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");

    @Test void acceptsEveryTypeSpecificShape() {
        assertThat(channel(NotificationChannelType.SLACK).getWebhookUrlCiphertext()).isEqualTo("sealed-url");
        assertThat(channel(NotificationChannelType.WEBHOOK).getWebhookUrlCiphertext()).isEqualTo("sealed-url");
        assertThat(channel(NotificationChannelType.EMAIL).getEmailTo()).isEqualTo("ops@example.com");
    }

    @Test void rejectsFieldsFromAnotherType() {
        assertThatThrownBy(() -> NotificationChannel.builder().id(UUID.randomUUID()).name("mail")
                .type(NotificationChannelType.EMAIL).emailTo("ops@example.com").webhookUrlCiphertext("unexpected")
                .createdAt(NOW).updatedAt(NOW).build()).hasMessageContaining("webhook URL");
    }

    @Test void acceptsOnlyAnEmailRecipientWithOneAtAndADottedDomain() {
        for (String bad : new String[] {"ops", "@example.com", "ops@example", "ops@.com", "ops@example.",
                "ops@ex@ample.com", "o ps@example.com", "ops@" + "a".repeat(200)}) {
            assertThatThrownBy(() -> NotificationChannel.builder().id(UUID.randomUUID()).name("mail")
                    .type(NotificationChannelType.EMAIL).emailTo(bad).createdAt(NOW).updatedAt(NOW).build())
                    .as(bad).hasMessageContaining("not valid");
        }
        assertThat(channel(NotificationChannelType.EMAIL).getEmailTo()).isEqualTo("ops@example.com");
    }

    @Test void validatesGenericAndSlackUrlsBeforeEncryption() {
        assertThat(NotificationChannel.validateWebhookUrl("http://localhost:8090/hook", false))
                .isEqualTo("http://localhost:8090/hook");
        assertThatThrownBy(() -> NotificationChannel.validateWebhookUrl("https://example.com/slack", true))
                .hasMessageContaining("official HTTPS host");
    }

    @Test void testCannotBePersistedAsASubscriptionEvent() {
        assertThatThrownBy(() -> new TargetNotificationSubscription(
                UUID.randomUUID(), Set.of(NotificationEventType.TEST)))
                .hasMessageContaining("TEST");
    }

    private static NotificationChannel channel(NotificationChannelType type) {
        var builder = NotificationChannel.builder().id(UUID.randomUUID()).name(type.name())
                .type(type).createdAt(NOW).updatedAt(NOW);
        return switch (type) {
            case SLACK, WEBHOOK -> builder.webhookUrlCiphertext("sealed-url").build();
            case EMAIL -> builder.emailTo("ops@example.com").build();
        };
    }
}
