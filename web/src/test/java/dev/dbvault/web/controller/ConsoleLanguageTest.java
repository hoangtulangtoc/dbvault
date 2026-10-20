package dev.dbvault.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.dbvault.application.backup.RunBackupService;
import dev.dbvault.application.storage.ManageStorageProfileService;
import dev.dbvault.application.target.ManageDatabaseTargetService;
import dev.dbvault.application.target.TestTargetConnectionService;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.web.security.SecurityConfig;

/** The console speaks English or Vietnamese, chosen per browser (DBV-31, ADR-042). */
@WebMvcTest(DatabaseTargetController.class)
@Import(SecurityConfig.class)
@WithMockUser
class ConsoleLanguageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageDatabaseTargetService service;

    @MockitoBean
    private TestTargetConnectionService connectionTest;

    @MockitoBean
    private RunBackupService backups;

    @MockitoBean
    private BackupExecutionRepository executions;

    @MockitoBean
    private ManageStorageProfileService storageProfiles;

    @BeforeEach
    void noTargets() {
        when(service.listAll()).thenReturn(List.of());
        when(storageProfiles.listAll()).thenReturn(List.of());
    }

    @Test
    void englishIsTheDefault() throws Exception {
        mockMvc.perform(get("/databases"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(containsString("Backup targets")));
    }

    @Test
    void langSwitchesThePageAndIsKeptInACookie() throws Exception {
        mockMvc.perform(get("/databases").param("lang", "vi"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"vi\"")))
                .andExpect(content().string(containsString("Đích sao lưu")))
                .andExpect(content().string(containsString("Chưa có đích nào.")))
                .andExpect(cookie().value("dbvault_lang", "vi"))
                .andExpect(cookie().httpOnly("dbvault_lang", true));

        mockMvc.perform(get("/databases").cookie(new Cookie("dbvault_lang", "vi")))
                .andExpect(content().string(containsString("Thêm đích")));
    }

    @Test
    void theBrowsersLanguageDecidesWhenNothingWasChosen() throws Exception {
        mockMvc.perform(get("/databases").header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.5"))
                .andExpect(content().string(containsString("Đích sao lưu")));
    }

    @Test
    void aLanguageTheConsoleDoesNotHaveIsEnglish() throws Exception {
        mockMvc.perform(get("/databases").param("lang", "fr"))
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(not(containsString("Đích sao lưu"))))
                .andExpect(cookie().value("dbvault_lang", "en"));
    }

    @Test
    void theTargetFormIsTranslatedToo() throws Exception {
        mockMvc.perform(get("/databases/new").param("lang", "vi"))
                .andExpect(content().string(containsString("Bắt buộc TLS cho mọi kết nối (khuyến nghị)")))
                .andExpect(content().string(containsString("Đăng ký đích")));
    }

    @Test
    void aFormCheckSpeaksTheBrowsersLanguage() throws Exception {
        mockMvc.perform(post("/databases").with(csrf()).cookie(new Cookie("dbvault_lang", "vi")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cần nhập tên")))
                .andExpect(content().string(containsString("Cần nhập máy chủ")));
    }

    @Test
    void aFlashMessageSpeaksTheLanguageOfTheRequestThatSetIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new NoSuchElementException("gone"));

        mockMvc.perform(get("/databases/{id}/edit", id).cookie(new Cookie("dbvault_lang", "vi")))
                .andExpect(flash().attribute("error", "Đích đó không còn nữa"));
        mockMvc.perform(get("/databases/{id}/edit", id))
                .andExpect(flash().attribute("error", "That target no longer exists"));
    }

    /** A stray quote in a pattern with arguments would swallow them; every pattern must format. */
    @Test
    void everyPatternWithArgumentsFormats() throws IOException {
        for (String bundle : List.of("/messages.properties", "/messages_vi.properties")) {
            Properties messages = load(bundle);
            for (String key : messages.stringPropertyNames()) {
                String pattern = messages.getProperty(key);
                if (!pattern.matches("(?s).*\\{\\d.*")) {
                    continue;
                }
                // Numbers, which a {0} and a {0,number,#} both accept. A quote that swallowed a
                // placeholder leaves it in the text.
                String text = new MessageFormat(pattern, Locale.ENGLISH).format(new Object[] {7, 8, 9});
                assertThat(text).as(bundle + " " + key).doesNotContain("{");
            }
        }
    }

    @Test
    void bothBundlesHaveTheSameKeys() throws IOException {
        assertThat(load("/messages_vi.properties").stringPropertyNames())
                .isEqualTo(load("/messages.properties").stringPropertyNames());
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ConsoleLanguageTest.class.getResourceAsStream(name)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
