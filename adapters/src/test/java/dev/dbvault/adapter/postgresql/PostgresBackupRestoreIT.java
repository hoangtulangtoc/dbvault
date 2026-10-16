package dev.dbvault.adapter.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.dbvault.adapter.process.ProcessRunner;
import dev.dbvault.core.model.DatabaseConnection;
import dev.dbvault.core.model.DatabaseEngine;

/** Real PostgreSQL plus the same host client binaries used in production. */
@Testcontainers
class PostgresBackupRestoreIT {

    private static final Path PSQL = Path.of("/usr/bin/psql");
    private static final Path PG_DUMP = Path.of("/usr/bin/pg_dump");
    private static final Path PG_RESTORE = Path.of("/usr/bin/pg_restore");
    private static final Pattern CLIENT_MAJOR = Pattern.compile("PostgreSQL\\) (\\d+)");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres:" + clientMajor() + "-alpine")
            .withDatabaseName("shop_source")
            .withUsername("backup")
            .withPassword("s3cr3t");

    @TempDir
    Path artifacts;

    private PostgresCliConnectionTestAdapter connectionTest;
    private PostgresDumpBackupAdapter backup;
    private PostgresRestoreAdapter restore;

    @BeforeAll
    static void requireClientBinaries() {
        assertThat(PSQL).isExecutable();
        assertThat(PG_DUMP).isExecutable();
        assertThat(PG_RESTORE).isExecutable();
    }

    @BeforeEach
    void setUp() throws Exception {
        ProcessRunner runner = new ProcessRunner();
        connectionTest = new PostgresCliConnectionTestAdapter(runner, PSQL, Duration.ofSeconds(10));
        backup = new PostgresDumpBackupAdapter(
                runner, PG_DUMP, Duration.ofSeconds(10), Duration.ofMinutes(2));
        restore = new PostgresRestoreAdapter(
                runner, PG_RESTORE, Duration.ofSeconds(10), Duration.ofMinutes(2));

        execute("shop_source", """
                DROP TABLE IF EXISTS widgets;
                CREATE TABLE widgets (id integer PRIMARY KEY, name text NOT NULL);
                INSERT INTO widgets VALUES (1, 'alpha'), (2, 'O''Brien'), (3, 'Đà Nẵng');
                """);
        try (Connection connection = jdbc("postgres"); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS shop_restore WITH (FORCE)");
            statement.execute("CREATE DATABASE shop_restore");
        }
    }

    @Test
    void testsConnectionDumpsAndRestoresIntoAnotherPostgresqlDatabase() throws Exception {
        assertThat(connectionTest.test(target("shop_source")).successful()).isTrue();
        Path artifact = artifacts.resolve("shop.dump");

        long size = backup.dumpTo(target("shop_source"), artifact);
        assertThat(size).isPositive();
        assertThat(artifact).isNotEmptyFile();

        execute("shop_restore", "CREATE TABLE widgets (id integer PRIMARY KEY, name text); INSERT INTO widgets VALUES (99, 'later');");
        restore.restore(target("shop_restore"), artifact);

        assertThat(query("shop_restore", "SELECT count(*) FROM widgets")).isEqualTo("3");
        assertThat(query("shop_restore", "SELECT string_agg(name, ',' ORDER BY id) FROM widgets"))
                .isEqualTo("alpha,O'Brien,Đà Nẵng");
        assertThat(query("shop_source", "SELECT count(*) FROM widgets")).isEqualTo("3");
    }

    /**
     * The round trip an operator relies on is more than rows in one table: types that pg_dump
     * writes in their own formats, the objects that hang off a table, and where a sequence had got to.
     */
    @Test
    void roundTripKeepsTypesSequencesIndexesViewsAndForeignKeys() throws Exception {
        execute("shop_source", """
                DROP VIEW IF EXISTS order_totals;
                DROP TABLE IF EXISTS order_lines;
                DROP TABLE IF EXISTS orders;
                CREATE TABLE orders (
                    id bigserial PRIMARY KEY,
                    placed_at timestamptz NOT NULL,
                    details jsonb NOT NULL,
                    receipt bytea);
                CREATE INDEX orders_placed_at ON orders (placed_at);
                CREATE TABLE order_lines (
                    order_id bigint NOT NULL REFERENCES orders(id),
                    sku text NOT NULL,
                    price numeric(12, 2) NOT NULL);
                CREATE VIEW order_totals AS
                    SELECT order_id, sum(price) AS total FROM order_lines GROUP BY order_id;
                INSERT INTO orders (placed_at, details, receipt) VALUES
                    ('2026-10-01 09:30:00+07', '{"channel": "web", "tags": ["gift", "đặc biệt"]}', '\\x00ff10'),
                    ('2026-10-02 18:05:00+00', '{"channel": "store"}', NULL);
                INSERT INTO order_lines VALUES (1, 'A-1', 19.99), (1, 'B-2', 0.01), (2, 'C-3', 1234567.89);
                """);
        Path artifact = artifacts.resolve("orders.dump");

        backup.dumpTo(target("shop_source"), artifact);
        restore.restore(target("shop_restore"), artifact);

        assertThat(query("shop_restore", "SELECT string_agg(total::text, ',' ORDER BY order_id) FROM order_totals"))
                .isEqualTo("20.00,1234567.89");
        assertThat(query("shop_restore", "SELECT details->'tags'->>1 FROM orders WHERE id = 1")).isEqualTo("đặc biệt");
        assertThat(query("shop_restore", "SELECT encode(receipt, 'hex') FROM orders WHERE id = 1")).isEqualTo("00ff10");
        assertThat(query("shop_restore", "SELECT placed_at AT TIME ZONE 'UTC' FROM orders WHERE id = 1"))
                .isEqualTo("2026-10-01 02:30:00");
        assertThat(query("shop_restore", "SELECT count(*) FROM pg_indexes WHERE indexname = 'orders_placed_at'"))
                .isEqualTo("1");
        assertThat(query("shop_restore",
                "SELECT count(*) FROM information_schema.table_constraints WHERE table_name = 'order_lines' AND constraint_type = 'FOREIGN KEY'"))
                .isEqualTo("1");
        // The next id continues after the restored rows rather than colliding with them.
        assertThat(query("shop_restore",
                "INSERT INTO orders (placed_at, details) VALUES (now(), '{}') RETURNING id")).isEqualTo("3");
    }

    private static DatabaseConnection target(String database) {
        return new DatabaseConnection(
                DatabaseEngine.POSTGRESQL,
                POSTGRES.getHost(),
                POSTGRES.getFirstMappedPort(),
                database,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static String clientMajor() {
        try {
            Process process = new ProcessBuilder(PG_DUMP.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String version = new String(process.getInputStream().readAllBytes()).strip();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Could not read pg_dump version: " + version);
            }
            Matcher matcher = CLIENT_MAJOR.matcher(version);
            if (!matcher.find()) {
                throw new IllegalStateException("Unrecognised pg_dump version: " + version);
            }
            return matcher.group(1);
        } catch (Exception e) {
            throw new IllegalStateException("The pg_dump client version could not be determined", e);
        }
    }

    private static Connection jdbc(String database) throws Exception {
        return DriverManager.getConnection(
                "jdbc:postgresql://%s:%d/%s".formatted(
                        POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), database),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(String database, String sql) throws Exception {
        try (Connection connection = jdbc(database); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String query(String database, String sql) throws Exception {
        try (Connection connection = jdbc(database);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
