package dev.dbvault.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.dbvault.adapter.TestAdaptersApplication;
import dev.dbvault.core.exception.NotificationChannelInUseException;
import dev.dbvault.core.exception.DuplicateNotificationChannelNameException;
import dev.dbvault.core.model.ConnectionCheck;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelType;
import dev.dbvault.core.model.NotificationEventType;
import dev.dbvault.core.model.TargetNotificationSubscription;
import dev.dbvault.core.port.DatabaseTargetRepository;
import dev.dbvault.core.port.NotificationChannelRepository;
import dev.dbvault.core.port.TargetNotificationSubscriptionRepository;

@SpringBootTest(classes = TestAdaptersApplication.class)
@Testcontainers
class NotificationChannelRepositoryAdapterIT {
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // This persistence slice never starts a database client. Keep its
        // context independent from which optional CLI packages exist on the host.
    }
    @Autowired NotificationChannelRepository channels;
    @Autowired TargetNotificationSubscriptionRepository subscriptions;
    @Autowired DatabaseTargetRepository targets;

    @Test void roundTripsEncryptedSettingsAndLastTest() {
        NotificationChannel saved = channels.save(webhook("roundtrip-" + UUID.randomUUID()));
        channels.recordConnectionCheck(saved.getId(), ConnectionCheck.failed("HTTP 503", NOW.plusSeconds(1)));
        NotificationChannel found = channels.findById(saved.getId()).orElseThrow();
        assertThat(found.getWebhookUrlCiphertext()).isEqualTo("sealed-webhook");
        assertThat(found.getLastConnectionCheck().message()).isEqualTo("HTTP 503");
        channels.deleteById(saved.getId());
    }

    @Test void replacesSubscriptionsAndTargetDeletionCascadesThem() {
        NotificationChannel channel = channels.save(webhook("linked-" + UUID.randomUUID()));
        DatabaseTarget target = targets.save(target("target-" + UUID.randomUUID()));
        subscriptions.replaceForTarget(target.getId(), List.of(new TargetNotificationSubscription(
                channel.getId(), Set.of(NotificationEventType.BACKUP_FAILED, NotificationEventType.RESTORE_FAILED))));
        assertThat(subscriptions.findByTargetId(target.getId())).singleElement().satisfies(link ->
                assertThat(link.events()).containsExactlyInAnyOrder(
                        NotificationEventType.BACKUP_FAILED, NotificationEventType.RESTORE_FAILED));
        assertThatThrownBy(() -> channels.deleteById(channel.getId()))
                .isInstanceOf(NotificationChannelInUseException.class);
        targets.deleteById(target.getId());
        assertThat(subscriptions.countForChannel(channel.getId())).isZero();
        channels.deleteById(channel.getId());
    }

    @Test void channelNamesAreUniqueWithoutRegardToCase() {
        NotificationChannel first = channels.save(webhook("Operations-" + UUID.randomUUID()));
        assertThatThrownBy(() -> channels.save(webhook(first.getName().toUpperCase())))
                .isInstanceOf(DuplicateNotificationChannelNameException.class);
        channels.deleteById(first.getId());
    }

    private static NotificationChannel webhook(String name) {
        return NotificationChannel.builder().id(UUID.randomUUID()).name(name).type(NotificationChannelType.WEBHOOK)
                .webhookUrlCiphertext("sealed-webhook").createdAt(NOW).updatedAt(NOW).build();
    }
    private static DatabaseTarget target(String name) {
        return DatabaseTarget.builder().id(UUID.randomUUID()).name(name).engine(DatabaseEngine.MYSQL)
                .host("localhost").port(3306).databaseName("shop").username("backup")
                .passwordCiphertext("sealed").createdAt(NOW).build();
    }
}
