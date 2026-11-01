package dev.dbvault.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.dbvault.application.retention.ManageBackupRetentionService;
import dev.dbvault.application.retention.ManageBackupRetentionService.RetentionView;
import dev.dbvault.core.model.BackupRetentionPolicy;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.web.controller.BackupRetentionController;

import jakarta.servlet.http.Cookie;

/** The sidebar's time zone, through a page that shows a time: the retention list's last run. */
@WebMvcTest(BackupRetentionController.class)
@WithMockUser
class ConsoleTimeZoneTest {

    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final Instant RUN = Instant.parse("2026-09-22T04:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ManageBackupRetentionService retention;

    @BeforeEach
    void setUp() {
        BackupRetentionPolicy policy = BackupRetentionPolicy.create(TARGET_ID, 7, RUN.minusSeconds(60)).completed(3, null, RUN);
        DatabaseTarget target = DatabaseTarget.builder().id(TARGET_ID).name("production").engine(DatabaseEngine.MYSQL)
                .host("db.internal").port(3306).databaseName("shop").username("backup").passwordCiphertext("sealed")
                .createdAt(RUN).build();
        when(retention.listAll()).thenReturn(List.of(new RetentionView(target, Optional.of(policy))));
    }

    @Test
    void timesAreInUtcUntilAZoneIsChosen() throws Exception {
        mockMvc.perform(get("/retention"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(containsString("2026-09-22 04:00:00 UTC")));
    }

    @Test
    void choosingAZoneInTheSidebarShowsTimesInItAndKeepsTheChoice() throws Exception {
        mockMvc.perform(get("/retention").param("tz", "Asia/Ho_Chi_Minh"))
                .andExpect(content().string(containsString("2026-09-22 11:00:00 Asia/Ho_Chi_Minh")))
                .andExpect(header().string("Set-Cookie", containsString("dbvault_tz=Asia/Ho_Chi_Minh")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
        mockMvc.perform(get("/retention").cookie(new Cookie("dbvault_tz", "Asia/Ho_Chi_Minh")))
                .andExpect(content().string(containsString("2026-09-22 11:00:00 Asia/Ho_Chi_Minh")))
                .andExpect(content().string(containsString("<option value=\"Asia/Ho_Chi_Minh\" selected=\"selected\">")));
    }

    @Test
    void anUnknownZoneIsIgnored() throws Exception {
        mockMvc.perform(get("/retention").param("tz", "Mars/Olympus_Mons"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().string(containsString("2026-09-22 04:00:00 UTC")))
                .andExpect(content().string(not(containsString("Mars/Olympus_Mons"))));
    }
}
