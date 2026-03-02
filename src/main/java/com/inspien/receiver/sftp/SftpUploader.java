package com.inspien.receiver.sftp;

import com.inspien.common.config.properties.SftpProperties;
import com.inspien.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class SftpUploader {

    private final SftpRemoteFileTemplate template;
    private final String remoteDir;

    public SftpUploader(SftpRemoteFileTemplate template, SftpProperties sftpProperties) {
        this.template = template;
        this.remoteDir = sftpProperties.getRemoteDir();
    }

    public void upload(Path localFile) {
        if (localFile == null || !Files.exists(localFile)) {
            throw ErrorCode.FILE_NOT_FOUND.exception();
        }

        template.execute(session -> {
            try {
                if (!session.exists(remoteDir)) {
                    session.mkdir(remoteDir);
                }

                String remotePath = remoteDir + "/" + localFile.getFileName();

                try (InputStream is = Files.newInputStream(localFile)) {
                    session.write(is, remotePath);
                }

                log.info("[SFTP] upload_ok local={} remote={}", localFile.getFileName(), remotePath);
                return null;
            } catch (Exception e) {
                log.error("[SFTP] upload_fail local={}", localFile.getFileName(), e);
                throw ErrorCode.SFTP_UPLOAD_FAIL.exception();
            }
        });
    }
}
