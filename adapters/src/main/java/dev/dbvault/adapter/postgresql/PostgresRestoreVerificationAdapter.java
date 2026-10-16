package dev.dbvault.adapter.postgresql;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.dbvault.adapter.process.ProcessRunner;
import dev.dbvault.adapter.verification.DockerVerificationSupport;
import dev.dbvault.core.model.DatabaseEngine;
import dev.dbvault.core.model.DatabaseTarget;
import dev.dbvault.core.model.RestoreVerificationResult;
import dev.dbvault.core.port.RestoreVerificationPort;

@Component
class PostgresRestoreVerificationAdapter implements RestoreVerificationPort {
    private static final String DATABASE = "verification";
    private final DockerVerificationSupport docker;
    private final boolean enabled;
    private final String image;
    private final Duration timeout;

    PostgresRestoreVerificationAdapter(DockerVerificationSupport docker,
            @Value("${dbvault.verification.enabled:false}") boolean enabled,
            @Value("${dbvault.verification.postgresql-image:postgres:17-alpine}") String image,
            @Value("${dbvault.restore.timeout}") Duration timeout) {
        this.docker = docker; this.enabled = enabled; this.image = image; this.timeout = timeout;
    }

    @Override public DatabaseEngine engine() { return DatabaseEngine.POSTGRESQL; }
    @Override public boolean isAvailable() { return enabled && docker.available(); }

    @Override
    public RestoreVerificationResult verify(UUID id, DatabaseTarget source, Path artifact) {
        String password = UUID.randomUUID().toString();
        String container = null;
        RuntimeException failure = null;
        try {
            container = docker.start(engine(), id, image,
                    Map.of("POSTGRES_PASSWORD", password, "POSTGRES_DB", DATABASE));
            Map<String, String> environment = Map.of("PGPASSWORD", password);
            docker.waitUntilReady(container, environment, "pg_isready", "-U", "postgres", "-d", DATABASE);
            docker.copy(artifact, container, "/tmp/backup.dump", Duration.ofMinutes(2));
            ProcessRunner.Result restored = docker.execWithEnvironment(container, timeout, environment,
                    "pg_restore", "-U", "postgres", "-d", DATABASE, "--no-owner", "--no-privileges",
                    "--exit-on-error", "/tmp/backup.dump");
            DockerVerificationSupport.requireSuccess(restored, "pg_restore failed");
            String check = """
                    DO $$ DECLARE r record; BEGIN
                      FOR r IN SELECT schemaname, tablename FROM pg_catalog.pg_tables
                               WHERE schemaname NOT IN ('pg_catalog','information_schema') LOOP
                        EXECUTE format('SELECT 1 FROM %I.%I LIMIT 1', r.schemaname, r.tablename);
                      END LOOP;
                    END $$;
                    SELECT count(*) FROM pg_catalog.pg_tables
                     WHERE schemaname NOT IN ('pg_catalog','information_schema');
                    """;
            ProcessRunner.Result checked = docker.execWithEnvironment(container, Duration.ofMinutes(2), environment,
                    "psql", "-U", "postgres", "-d", DATABASE, "-v", "ON_ERROR_STOP=1", "-At", "-c", check);
            DockerVerificationSupport.requireSuccess(checked, "PostgreSQL health check failed");
            int count = Integer.parseInt(checked.stdout().lines().filter(line -> line.matches("\\d+"))
                    .reduce((first, second) -> second).orElse("0"));
            return new RestoreVerificationResult(count,
                    "Restored successfully and read %d PostgreSQL table(s)".formatted(count));
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            try { docker.remove(engine(), id); }
            catch (RuntimeException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup); else throw cleanup;
            }
        }
    }

    @Override public void abortInterrupted(UUID id) { docker.remove(engine(), id); }
}
