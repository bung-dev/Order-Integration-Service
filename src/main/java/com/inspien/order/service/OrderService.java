package com.inspien.order.service;

import com.inspien.common.exception.ErrorCode;
import com.inspien.mapper.OrderMapper;
import com.inspien.mapper.OrderParserXML;
import com.inspien.mapper.OrderRequestValidator;
import com.inspien.mapper.dto.FlattenResult;
import com.inspien.mapper.dto.OrderRequestXML;
import com.inspien.order.strategy.OrderProcessor;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    private final OrderParserXML orderParserXML;
    private final OrderRequestValidator validator;
    private final OrderMapper mapper;
    private final OrderProcessor syncOrderProcessor;
    private final OrderProcessor asyncOrderProcessor;

    public OrderService(OrderParserXML orderParserXML,
                        OrderRequestValidator validator,
                        OrderMapper mapper,
                        @Qualifier("syncOrderProcessor") OrderProcessor syncOrderProcessor,
                        @Qualifier("asyncOrderProcessor") OrderProcessor asyncOrderProcessor) {
        this.orderParserXML = orderParserXML;
        this.validator = validator;
        this.mapper = mapper;
        this.syncOrderProcessor = syncOrderProcessor;
        this.asyncOrderProcessor = asyncOrderProcessor;
    }

    public CreateOrderResult createOrderSync(String base64Xml) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[ORDER] start base64Size={}", base64Xml == null ? 0 : base64Xml.length());
            FlattenResult flattenResult = parseAndFlatten(base64Xml, "[ORDER]");
            CreateOrderResult result = syncOrderProcessor.process(flattenResult.orders(), flattenResult.skippedCount());
            log.info("[ORDER] done success={} message={} orderCount={} skippedCount={} retryCount={}",
                    result.success(), result.message(), result.orderCount(), result.skippedCount(), result.retryCount());
            log.info("[ORDER] durationTime={}", System.currentTimeMillis() - startTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    public CreateOrderResult createOrderOutbox(String base64Xml) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[ORDER OUTBOX] start base64Size={}", base64Xml == null ? 0 : base64Xml.length());
            FlattenResult flattenResult = parseAndFlatten(base64Xml, "[ORDER OUTBOX]");
            CreateOrderResult result = asyncOrderProcessor.process(flattenResult.orders(), flattenResult.skippedCount());
            log.info("[ORDER OUTBOX] done success={} message={} orderCount={} skippedCount={} retryCount={}",
                    result.success(), result.message(), result.orderCount(), result.skippedCount(), result.retryCount());
            log.info("[ORDER OUTBOX] durationTime={}", System.currentTimeMillis() - startTime);
            return result;
        } finally {
            MDC.remove("traceId");
        }
    }

    private FlattenResult parseAndFlatten(String base64Xml, String logPrefix) {
        final OrderRequestXML request;
        try {
            request = orderParserXML.parse(base64Xml);
        } catch (Exception e) {
            log.error("{} xml_parse_fail", logPrefix, e);
            throw ErrorCode.XML_PARSE_ERROR.exception();
        }
        validator.validate(request);
        log.info("{} validate_ok", logPrefix);
        FlattenResult flattenResult = mapper.flatten(request);
        log.info("{} flatten_ok orders={} skipped={}", logPrefix,
                flattenResult.orders().size(), flattenResult.skippedCount());
        return flattenResult;
    }
}
