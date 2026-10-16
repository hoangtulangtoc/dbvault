package dev.dbvault.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.dbvault.adapter.TestAdaptersApplication;
import dev.dbvault.core.exception.DuplicateScheduleNameException;
import dev.dbvault.core.model.BackupSchedule;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.port.BackupScheduleRepository;
import dev.dbvault.core.port.DatabaseTargetRepository;

@SpringBootTest(classes = TestAdaptersApplication.class)
@Testcontainers
class BackupScheduleRepositoryAdapterIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private BackupScheduleRepository schedules;
    @Autowired private DatabaseTargetRepository targets;

    @BeforeEach
    void removeSchedulesLeftByAnotherTest() {
        schedules.findAll().forEach(schedule -> schedules.deleteById(schedule.getId()));
    }

    @Test
    void roundTripsAndOrdersSchedulesByName() {
        DatabaseTarget target = targets.save(target("schedule target"));
        BackupSchedule zulu = schedules.save(schedule("Zulu", target.getId(), true));
        schedules.save(schedule("alpha", target.getId(), false));

        assertThat(schedules.findAll()).extracting(BackupSchedule::getName)
                .containsExactly("alpha", "Zulu");
        BackupSchedule found = schedules.findById(zulu.getId()).orElseThrow();
        assertThat(found.getCronExpression()).isEqualTo("0 0 2 * * ?");
        assertThat(found.getZoneId()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(found.isEnabled()).isTrue();
        assertThat(schedules.countForTarget(target.getId())).isEqualTo(2);
    }

    @Test
    void nameIsUniqueIgnoringCaseAndWhitespace() {
        DatabaseTarget target = targets.save(target("unique schedule target"));
        schedules.save(schedule("Nightly", target.getId(), true));

        assertThatThrownBy(() -> schedules.save(schedule(" nightly ", target.getId(), true)))
                .isInstanceOf(DuplicateScheduleNameException.class);
    }

    private static BackupSchedule schedule(String name, UUID targetId, boolean enabled) {
        Instant now = Instant.parse("2026-09-21T01:00:00Z");
        return BackupSchedule.builder()
                .id(UUID.randomUUID())
                .targetId(targetId)
                .name(name)
                .cronExpression("0 0 2 * * ?")
                .zoneId("Asia/Ho_Chi_Minh")
                .enabled(enabled)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static DatabaseTarget target(String name) {
        return DatabaseTarget.builder()
                .id(UUID.randomUUID())
                .name(name)
                .engine(DatabaseEngine.MYSQL)
                .host("127.0.0.1")
                .port(3306)
                .databaseName("shop")
                .username("backup")
                .passwordCiphertext("sealed")
                .createdAt(Instant.parse("2026-09-21T01:00:00Z"))
                .build();
    }
}
