package com.inspien.common.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {
    private String host;
    private int port;
    private String username;
    private String password;
    private String remoteDir;
}
