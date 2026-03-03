package com.inspien.order.service;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.mapper.OrderDomainMapper;
import com.inspien.mapper.dto.FlattenResult;
import com.inspien.order.domain.Outbox;
import com.inspien.receiver.jdbc.BatchResult;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.receiver.sftp.FileWriter;
import com.inspien.receiver.sftp.SftpUploader;
import com.inspien.common.exception.ErrorCode;
import com.inspien.mapper.OrderMapper;
import com.inspien.mapper.OrderParserXML;
import com.inspien.mapper.OrderRequestValidator;
import com.inspien.mapper.dto.OrderRequestXML;
import com.inspien.order.domain.Order;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderCommandService orderCommandService;
    private final OrderParserXML orderParserXML;
    private final OrderRequestValidator validator;
    private final OrderMapper mapper;
    private final OrderDomainMapper orderDomainMapper;
    private final FileWriter fileWriter;
    private final SftpUploader sftpUploader;
    private final OutboxRepository outboxRepository;
    private final AppProperties appProperties;

    public CreateOrderResult createOrderOutbox(String base64Xml) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[ORDER OUTBOX] start base64Size={}",
                    base64Xml == null ? 0 : base64Xml.length());

            final OrderRequestXML request;
            try {
                request = orderParserXML.parse(base64Xml);
            } catch (Exception e) {
                log.error("[ORDER OUTBOX] xml_parse_fail", e);
                throw ErrorCode.XML_PARSE_ERROR.exception();
            }

            validator.validate(request);
            log.info("[ORDER OUTBOX] validate_ok");

            FlattenResult flattenResult = mapper.flatten(request);
            List<Order> orders = flattenResult.orders();
            int skippedCount = flattenResult.skippedCount();
            log.info("[ORDER OUTBOX] flatten_ok orders={} skipped={}", orders.size(), skippedCount);

            CreateOrderResult result = saveOrdersOutbox(orders, skippedCount);

            log.info("[ORDER OUTBOX] done success={} message={} orderCount={} skippedCount={} retryCount={}",
                    result.success(),
                    result.message(),
                    result.orderCount(),
                    result.skippedCount(),
                    result.retryCount()
            );

            long endTime = System.currentTimeMillis();
            log.info("[ORDER OUTBOX] durationTime={}", endTime - startTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    public void setSftpUploader(Path file) {
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
    }

    public CreateOrderResult createOrderSync(String base64Xml) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[ORDER] start base64Size={}",
                    base64Xml == null ? 0 : base64Xml.length());

            final OrderRequestXML request;
            try {
                request = orderParserXML.parse(base64Xml);
            } catch (Exception e) {
                log.error("[ORDER] xml_parse_fail", e);
                throw ErrorCode.XML_PARSE_ERROR.exception();
            }

            validator.validate(request);
            log.info("[ORDER] validate_ok");

            FlattenResult flattenResult = mapper.flatten(request);
            List<Order> orders = flattenResult.orders();
            int skippedCount = flattenResult.skippedCount();
            log.info("[ORDER] flatten_ok orders={} skipped={}", orders.size(), skippedCount);

            CreateOrderResult result = saveOrders(orders, skippedCount);

            log.info("[ORDER] done success={} message={} orderCount={} skippedCount={} retryCount={}",
                    result.success(),
                    result.message(),
                    result.orderCount(),
                    result.skippedCount(),
                    result.retryCount()
            );

            long endTime = System.currentTimeMillis();
            log.info("[ORDER] durationTime={}", endTime - startTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    private CreateOrderResult saveOrdersOutbox(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }
        return processOrderSave(orders, skippedCount, savedOrders -> {
            List<Outbox> outboxes = savedOrders.stream()
                    .map(orderDomainMapper::toOutbox)
                    .toList();
            outboxRepository.batchInsert(outboxes);
        });
    }

    private CreateOrderResult saveOrders(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }
        String participantName = appProperties.getParticipantName();
        return processOrderSave(orders, skippedCount, savedOrders -> {
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

    private CreateOrderResult processOrderSave(List<Order> orders, int skippedCount, Consumer<List<Order>> extraAction) {
        int maxRetry = appProperties.getMaxRetry();
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                BatchResult summary = orderCommandService.saveOrdersWithId(orders);
                log.info("[ORDER:DB] insert_done attempt={} total={} success={} fail={}",
                        attempt, summary.totalCount(), summary.successCount(), summary.failCount());
                if (extraAction != null) {
                    extraAction.accept(orders);
                }
                return CreateOrderResult.ok(summary.successCount(), skippedCount, attempt);
            } catch (DuplicateKeyException e) {
                if (attempt == maxRetry) throw e;
            }
        }
        throw ErrorCode.INTERNAL_ERROR.exception();
    }
}
