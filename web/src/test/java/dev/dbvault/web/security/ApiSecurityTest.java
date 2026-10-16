package dev.dbvault.web.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.dbvault.application.target.ManageDatabaseTargetService;
import dev.dbvault.core.model.BackupExecution;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.BackupExecutionRepository;
import dev.dbvault.web.api.TargetApiController;

@WebMvcTest(TargetApiController.class)
@Import({ ApiSecurityConfig.class, SecurityConfig.class, OperatorAccountConfig.class })
@TestPropertySource(properties = {
        "dbvault.operator.username=operator",
        "dbvault.operator.password-hash=" + ApiSecurityTest.PASSWORD_HASH })
class ApiSecurityTest {

    static final String PASSWORD = "correct horse battery staple";
    /** bcrypt, cost 10, of {@link #PASSWORD}. */
    static final String PASSWORD_HASH = "$2a$10$GBoqx2dLOGXvTrLAxfpxOe8TRLOBQUfAFHKh.DYi1nWt0BRyCALTy";

    private static final Instant CREATED = Instant.parse("2026-10-01T08:00:00Z");
    private static final UUID SHOP = UUID.randomUUID();
    private static final String SHA256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageDatabaseTargetService targets;

    @MockitoBean
    private BackupExecutionRepository executions;

    private static DatabaseTarget shop() {
        return DatabaseTarget.builder().id(SHOP).name("shop").host("db.internal").port(3306)
                .databaseName("shop").username("backup").passwordCiphertext("ciphertext")
                .createdAt(CREATED).build();
    }

    @Test
    void anAnonymousCallerIsAskedForBasicCredentialsNotRedirected() throws Exception {
        mockMvc.perform(get("/api/targets"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")));
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        mockMvc.perform(get("/api/targets").with(httpBasic("operator", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsTargetsWithoutTheirPassword() throws Exception {
        when(targets.listAll()).thenReturn(List.of(shop()));

        mockMvc.perform(get("/api/targets").with(httpBasic("operator", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$[0].name").value("shop"))
                .andExpect(jsonPath("$[0].database").value("shop"))
                .andExpect(jsonPath("$[0].passwordCiphertext").doesNotExist());
    }

    @Test
    void listsOneTargetsBackupsNewestFirstWithoutTheirPath() throws Exception {
        BackupExecution newer = BackupExecution.started(UUID.randomUUID(), SHOP, CREATED.plusSeconds(120))
                .succeeded("/backups/shop.sql.gz", 2048, SHA256, CREATED.plusSeconds(130));
        BackupExecution older = BackupExecution.started(UUID.randomUUID(), SHOP, CREATED.plusSeconds(60))
                .failed("Access denied", CREATED.plusSeconds(61));
        when(targets.listAll()).thenReturn(List.of(shop()));
        when(executions.findAllForTarget(SHOP)).thenReturn(List.of(older, newer));

        mockMvc.perform(get("/api/targets/{id}/backups", SHOP).with(httpBasic("operator", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$[0].sizeBytes").value(2048))
                .andExpect(jsonPath("$[0].sha256").value(SHA256))
                .andExpect(jsonPath("$[0].artifactPath").doesNotExist())
                .andExpect(jsonPath("$[1].error").value("Access denied"));
    }

    @Test
    void anUnknownTargetIsA404() throws Exception {
        when(targets.listAll()).thenReturn(List.of(shop()));

        mockMvc.perform(get("/api/targets/{id}/backups", UUID.randomUUID()).with(httpBasic("operator", PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
