package com.inspien.sender;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.order.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentScheduler {
    private final ShipmentService shipmentService;
    private final AppProperties appProperties;

    @Scheduled(fixedDelayString = "${app.scheduler-delay}")
    public void run() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER] start");
            shipmentService.run(appProperties.getKey());
        } finally {
            MDC.remove("traceId");
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler-delay}")
    public void runOutbox() {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER OUTBOX] start");
            shipmentService.runOutbox(appProperties.getKey());
        } finally {
            long endTime = System.currentTimeMillis();
            long durationTime = endTime - startTime;
            log.info("[BATCH:SCHEDULER OUTBOX] durationTime={}", durationTime);
            MDC.remove("traceId");
        }
    }

    @Scheduled(fixedDelayString = "${app.cleanup-delay:3600000}")
    public void runOutboxCleanup() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER CLEANUP] start");
            shipmentService.cleanupOldOutbox(appProperties.getKey());
        } finally {
            MDC.remove("traceId");
        }
    }
}
