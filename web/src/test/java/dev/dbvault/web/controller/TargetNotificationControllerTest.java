package dev.dbvault.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.dbvault.application.notification.ManageNotificationChannelService;
import dev.dbvault.application.notification.ManageTargetNotificationService;
import dev.dbvault.application.target.ManageDatabaseTargetService;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.NotificationChannel;
import dev.dbvault.core.model.NotificationChannelType;
import dev.dbvault.web.security.SecurityConfig;

@WebMvcTest(TargetNotificationController.class)
@Import(SecurityConfig.class)
class TargetNotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ManageTargetNotificationService targetNotifications;
    @MockitoBean ManageNotificationChannelService channels;
    @MockitoBean ManageDatabaseTargetService targets;

    @Test @WithMockUser void newSubscriptionDefaultsToAllThreeFailureEvents() throws Exception {
        UUID targetId = UUID.randomUUID(); UUID channelId = UUID.randomUUID();
        when(targets.get(targetId)).thenReturn(target(targetId));
        when(targetNotifications.subscriptionsFor(targetId)).thenReturn(List.of());
        when(channels.listAll()).thenReturn(List.of(channel(channelId)));
        mvc.perform(get("/databases/{id}/notifications", targetId)).andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*value=\"BACKUP_FAILED\"[^>]*checked=\"checked\".*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*value=\"RESTORE_FAILED\"[^>]*checked=\"checked\".*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*value=\"VERIFICATION_FAILED\"[^>]*checked=\"checked\".*")));
    }

    @Test @WithMockUser void savesSelectedEventsWithCsrf() throws Exception {
        UUID targetId = UUID.randomUUID(); UUID channelId = UUID.randomUUID();
        mvc.perform(post("/databases/{id}/notifications", targetId).with(csrf())
                        .param("rows[0].channelId", channelId.toString())
                        .param("rows[0].included", "true")
                        .param("rows[0].events", "BACKUP_FAILED", "RESTORE_FAILED"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/databases"));
        verify(targetNotifications).replace(eq(targetId), any());
    }

    private static DatabaseTarget target(UUID id) {
        return DatabaseTarget.builder().id(id).name("production").engine(DatabaseEngine.MYSQL)
                .host("db").port(3306).databaseName("shop").username("backup")
                .passwordCiphertext("sealed").createdAt(Instant.parse("2026-09-24T01:00:00Z")).build();
    }
    private static NotificationChannel channel(UUID id) {
        Instant now = Instant.parse("2026-09-24T01:00:00Z");
        return NotificationChannel.builder().id(id).name("operations").type(NotificationChannelType.EMAIL)
                .emailTo("ops@example.com").createdAt(now).updatedAt(now).build();
    }
}
