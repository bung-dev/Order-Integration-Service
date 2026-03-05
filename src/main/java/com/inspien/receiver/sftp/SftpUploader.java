package com.inspien.receiver.sftp;

import com.inspien.common.config.properties.SftpProperties;
import com.inspien.common.exception.ErrorCode;
import com.inspien.common.exception.SftpException;
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
            String finalName = localFile.getFileName().toString();
            String tempPath = remoteDir + "/" + finalName + ".tmp";
            String remotePath = remoteDir + "/" + finalName;
            try {
                if (!session.exists(remoteDir)) {
                    session.mkdir(remoteDir);
                }
                try (InputStream is = Files.newInputStream(localFile)) {
                    session.write(is, tempPath);
                }
                // SFTP v3 rename: not guaranteed atomic, but widely supported
                session.rename(tempPath, remotePath);
                log.info("[SFTP] upload_ok local={} remote={}", finalName, remotePath);
                return null;
            } catch (Exception e) {
                log.error("[SFTP] upload_fail local={}", finalName, e);
                try {
                    session.remove(tempPath);
                } catch (Exception ignore) {}
                throw new SftpException(ErrorCode.SFTP_UPLOAD_FAIL, true);
            }
        });
    }
}
