package com.inspien.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String key;
    private int maxRetry = 10;
    private String participantName;
    private String outDir;
    private int retentionDays = 7;
    private String schedulerDelay;
    private String cleanupDelay;
}
