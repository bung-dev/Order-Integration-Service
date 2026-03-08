package com.inspien.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SftpHealthIndicator implements HealthIndicator {

    private final SftpRemoteFileTemplate sftpRemoteFileTemplate;

    @Override
    public Health health() {
        try {
            sftpRemoteFileTemplate.execute(session -> {
                session.list(".");
                return null;
            });
            return Health.up()
                    .withDetail("service", "SFTP")
                    .build();
        } catch (Exception e) {
            log.warn("[HEALTH] SFTP connection failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("service", "SFTP")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
