package com.inspien.common.config;

import com.inspien.common.config.properties.SftpProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

@Configuration
public class SftpConfig {

    private final SftpProperties sftpProperties;

    public SftpConfig(SftpProperties sftpProperties) {
        this.sftpProperties = sftpProperties;
    }

    @Bean
    public SessionFactory<SftpClient.DirEntry> sftpSessionFactory() {
        DefaultSftpSessionFactory sftpSessionFactory = new DefaultSftpSessionFactory();
        sftpSessionFactory.setUser(sftpProperties.getUsername());
        sftpSessionFactory.setPassword(sftpProperties.getPassword());
        sftpSessionFactory.setHost(sftpProperties.getHost());
        sftpSessionFactory.setPort(sftpProperties.getPort());
        sftpSessionFactory.setAllowUnknownKeys(true);
        return new CachingSessionFactory<>(sftpSessionFactory);
    }

    @Bean
    public SftpRemoteFileTemplate sftpRemoteFileTemplate() {
        return new SftpRemoteFileTemplate(sftpSessionFactory());
    }
}
