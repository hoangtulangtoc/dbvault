package dev.dbvault.web.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** The licence report the build writes into the application jar (docs/licences.md). */
class ThirdPartyReportTest {

    @Test
    void listsTheBundledLibrariesWithTheirLicencesAndNotDbvaultItself() throws IOException {
        String report;
        try (InputStream in = ThirdPartyReportTest.class.getResourceAsStream("/THIRD-PARTY.txt")) {
            assertThat(in).as("THIRD-PARTY.txt on the classpath").isNotNull();
            report = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(report)
                .contains("org.springframework:spring-core:")
                .containsPattern("ch\\.qos\\.logback:logback-core:\\S+ .*\\[LGPL-2\\.1-only\\]")
                .doesNotContain("dev.dbvault:")
                .doesNotContain("org.projectlombok:");
    }
}
