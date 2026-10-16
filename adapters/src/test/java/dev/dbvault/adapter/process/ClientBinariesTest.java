package dev.dbvault.adapter.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientBinariesTest {

    @TempDir
    Path dir;

    @Test
    void acceptsAnExecutable() {
        assertThat(ClientBinaries.require(Path.of("/bin/sh"), "dbvault.mysql.client-path")).isEqualTo(Path.of("/bin/sh"));
    }

    @Test
    void namesThePropertyWhenThePathIsMissingOrNotExecutable() throws Exception {
        Path plain = Files.writeString(dir.resolve("mysqldump"), "not a program");

        assertThatThrownBy(() -> ClientBinaries.require(dir.resolve("absent"), "dbvault.mysql.dump-path"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dbvault.mysql.dump-path");
        assertThatThrownBy(() -> ClientBinaries.require(plain, "dbvault.postgresql.dump-path"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an executable file");
    }
}
