package com.inspien.order.service;

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
import com.inspien.receiver.jdbc.OrderRepository;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PlatformTransactionManager txManager;
    private final OrderParserXML orderParserXML;
    private final OrderRequestValidator validator;
    private final OrderMapper mapper;
    private final IdGenerator idGenerator;
    private final FileWriter fileWriter;
    private final SftpUploader sftpUploader;
    private final OutboxRepository outboxRepository;

    @Value("${order.max-retry:50}")
    private int maxRetry;

    @Value("${order.participant-name}")
    private String participantName;

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
            long durationTime = endTime - startTime;

            log.info("[ORDER OUTBOX] durationTime={}", durationTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    public void setSftpUploader(Path file){
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
            long durationTime = endTime - startTime;

            log.info("[ORDER] durationTime={}",durationTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    private CreateOrderResult saveOrdersOutbox(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }

        TransactionTemplate requiresNewTx = requiresNewTemplate();

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            final int attemptNum = attempt;
            assignOrderId(orders);

            try {
                return requiresNewTx.execute(status -> {
                    BatchResult summary = executeBatchInsert(orders);

                    List<Outbox> outboxes = orders.stream()
                            .map(o -> Outbox.builder()
                                    .applicantKey(o.getApplicantKey())
                                    .orderId(o.getOrderId())
                                    .userId(o.getUserId())
                                    .itemId(o.getItemId())
                                    .name(o.getName())
                                    .address(o.getAddress())
                                    .itemName(o.getItemName())
                                    .price(o.getPrice())
                                    .status(o.getStatus())
                                    .processed(false)
                                    .build())
                            .toList();

                    outboxRepository.batchInsert(outboxes);

                    log.info("[ORDER OUTBOX:DB] insert_done attempt={} total={} success={} fail={}",
                            attemptNum,
                            summary.totalCount(),
                            summary.successCount(),
                            summary.failCount());

                    return CreateOrderResult.ok(summary.successCount(), skippedCount, attemptNum);
                });
            } catch (DuplicateKeyException e) {
                if (attempt == maxRetry) {
                    log.warn("[ORDER OUTBOX:DB] duplicate_key_retry attempt={}/{}", attempt, maxRetry);
                    throw ErrorCode.DUPLICATE_KEY.exception();
                }
            }
        }
        throw ErrorCode.INTERNAL_ERROR.exception();
    }

    private CreateOrderResult saveOrders(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }

        TransactionTemplate requiresNewTx = requiresNewTemplate();

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            final int attemptNum = attempt;
            assignOrderId(orders);

            try {
                return requiresNewTx.execute(status -> {
                    BatchResult summary = executeBatchInsert(orders);
                    log.info("[ORDER:DB] insert_done attempt={} total={} success={} fail={}",
                            attemptNum,
                            summary.totalCount(),
                            summary.successCount(),
                            summary.failCount());
                    Path file = fileWriter.write(orders, participantName);
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
                    return CreateOrderResult.ok(summary.successCount(), skippedCount, attemptNum);
                });

            } catch (DuplicateKeyException e) {
                if (attempt == maxRetry) {
                    log.warn("[ORDER:DB] duplicate_key_retry attempt={}/{}", attempt, maxRetry);
                    throw ErrorCode.DUPLICATE_KEY.exception();
                }
            }
        }
        throw ErrorCode.INTERNAL_ERROR.exception();
    }

    private TransactionTemplate requiresNewTemplate() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    private BatchResult executeBatchInsert(List<Order> orders) {
        int[] result = orderRepository.batchInsert(orders);
        return BatchResult.from(result, orders.size());
    }

    private void assignOrderId(List<Order> orders) {
        for (Order o : orders) {
            o.setOrderId(idGenerator.generate());
        }
    }
}
