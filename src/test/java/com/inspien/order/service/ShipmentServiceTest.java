package com.inspien.order.service;

import com.inspien.receiver.jdbc.OrderRepository;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.receiver.jdbc.ShipmentRepository;
import com.inspien.receiver.jdbc.dto.PendingOrderRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @InjectMocks
    private ShipmentService shipmentService;

    @Mock private OrderRepository orderRepository;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private IdGenerator idGenerator;

    @Test
    @DisplayName("출고 대기 주문 처리 성공 시 상태 업데이트 확인")
    void run_BatchSuccess_UpdateStatus() {
        // given
        String appKey = "LJH000009";
        List<PendingOrderRow> pending = List.of(
            new PendingOrderRow("ORD1", "ITEM1", appKey, "Addr1"),
            new PendingOrderRow("ORD2", "ITEM2", appKey, "Addr2")
        );
        
        when(orderRepository.findPendingForShipment(appKey)).thenReturn(pending);
        // 첫 번째는 성공(1), 두 번째는 실패(0) 가정
        when(shipmentRepository.batchInsert(anyList())).thenReturn(new int[]{1, 0});

        // when
        shipmentService.run(appKey);

        // then
        verify(orderRepository).updateStatusToY(eq(appKey), argThat(list -> 
            list.size() == 1 && list.contains("ORD1")
        ));
    }

    @Test
    @DisplayName("처리 완료된 Outbox 데이터 삭제 확인")
    void cleanupProcessedOutbox_CallsRepository() {
        // given
        String appKey = "TEST_KEY";

        // when
        shipmentService.cleanupProcessedOutbox(appKey);

        // then
        verify(outboxRepository).deleteProcessed(appKey);
    }
}
