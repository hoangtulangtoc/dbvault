package dev.dbvault.adapter.mysql;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.dbvault.adapter.verification.AbstractDockerSqlVerificationAdapter;
import dev.dbvault.adapter.verification.DockerVerificationSupport;
import dev.dbvault.core.model.DatabaseEngine;

@Component
class MysqlRestoreVerificationAdapter extends AbstractDockerSqlVerificationAdapter {
    MysqlRestoreVerificationAdapter(DockerVerificationSupport docker,
            @Value("${dbvault.verification.enabled:false}") boolean enabled,
            @Value("${dbvault.verification.mysql-image:mysql:8.4}") String image,
            @Value("${dbvault.restore.timeout}") Duration timeout) {
        super(docker, enabled, image, "MYSQL_ROOT_PASSWORD", "mysql", timeout);
    }
    @Override public DatabaseEngine engine() { return DatabaseEngine.MYSQL; }
}
