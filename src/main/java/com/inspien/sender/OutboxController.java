package com.inspien.sender;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.order.domain.Outbox;
import com.inspien.order.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/outbox")
public class OutboxController {

    private final ShipmentService shipmentService;
    private final AppProperties appProperties;

    // FAILED 상태 Outbox 목록 조회
    @GetMapping("/failed")
    public ResponseEntity<List<Outbox>> getFailedOutboxes() {
        List<Outbox> failed = shipmentService.getFailedOutboxes(appProperties.getKey());
        return ResponseEntity.ok(failed);
    }

    // 특정 orderId를 UNPROCESSED로 리셋하여 재처리 대기열에 추가
    @PostMapping("/retry/{orderId}")
    public ResponseEntity<Void> retryFailed(@PathVariable String orderId) {
        shipmentService.retryFailed(orderId);
        return ResponseEntity.ok().build();
    }
}
