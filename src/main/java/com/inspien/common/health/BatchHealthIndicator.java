package com.inspien.common.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class BatchHealthIndicator implements HealthIndicator {

    private volatile LocalDateTime lastSuccessTime;

    public void recordSuccess() {
        this.lastSuccessTime = LocalDateTime.now();
    }

    @Override
    public Health health() {
        if (lastSuccessTime == null) {
            return Health.unknown()
                    .withDetail("reason", "No batch has run yet")
                    .build();
        }
        if (lastSuccessTime.isBefore(LocalDateTime.now().minusHours(1))) {
            return Health.down()
                    .withDetail("reason", "No batch success for over 1 hour")
                    .withDetail("last_success", lastSuccessTime.toString())
                    .build();
        }
        return Health.up()
                .withDetail("last_success", lastSuccessTime.toString())
                .build();
    }
}
