package com.inspien.order.strategy;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.common.exception.ErrorCode;
import com.inspien.order.domain.Order;
import com.inspien.order.service.OrderCommandService;
import com.inspien.receiver.sftp.FileWriter;
import com.inspien.receiver.sftp.SftpUploader;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service("syncOrderProcessor")
@RequiredArgsConstructor
public class SyncOrderProcessor implements OrderProcessor {

    private final OrderCommandService orderCommandService;
    private final FileWriter fileWriter;
    private final SftpUploader sftpUploader;
    private final AppProperties appProperties;

    @Override
    public CreateOrderResult process(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }
        String participantName = appProperties.getParticipantName();
        return orderCommandService.saveOrdersWithRetry(orders, skippedCount, savedOrders -> {
            Path file = fileWriter.write(savedOrders, participantName);
            log.info("[ORDER:FILE] created file={}", file.getFileName());
            try {
                sftpUploader.upload(file);
                log.info("[ORDER:SFTP] upload_ok file={}", file.getFileName());
            } catch (RuntimeException e) {
                log.error("[ORDER:SFTP] upload_fail file={}", file.getFileName(), e);
                try {
                    Files.deleteIfExists(file);
                    log.info("[ORDER:FILE] deleted file={}", file.getFileName());
                } catch (Exception deleteEx) {
                    log.warn("[ORDER:FILE] delete_fail file={}", file.getFileName(), deleteEx);
                }
                throw ErrorCode.SFTP_SEND_FAIL.exception();
            }
        });
    }
}
