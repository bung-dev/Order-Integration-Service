package com.inspien.sender;

import com.inspien.order.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentScheduler {
    private final ShipmentService shipmentService;

    @Value("${application.key}")
    private String applicationKey;

    @Scheduled(fixedDelayString = "${shipment.scheduler.delay}")
    public void run() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER] start");
            shipmentService.run(applicationKey);
        } finally {
            MDC.remove("traceId");
        }
    }

    @Scheduled(fixedDelayString = "${shipment.scheduler.delay}")
    public void runOutbox(){
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER OUTBOX] start");
            shipmentService.runOutbox(applicationKey);
        } finally {
            long endTime = System.currentTimeMillis();
            long durationTime = endTime - startTime;
            log.info("[BATCH:SCHEDULER OUTBOX] durationTime={}",durationTime);
            MDC.remove("traceId");
        }
    }

    @Scheduled(fixedDelayString = "${shipment.scheduler.delay}")
    public void runDeleteProcessed(){
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        try {
            log.info("[BATCH:SCHEDULER DeleteProcessed] start");
            shipmentService.cleanupProcessedOutbox(applicationKey);
        } finally {
            long endTime = System.currentTimeMillis();
            long durationTime = endTime - startTime;
            log.info("[BATCH:SCHEDULER DeleteProcessed] durationTime={}",durationTime);
            MDC.remove("traceId");
        }
    }
}
