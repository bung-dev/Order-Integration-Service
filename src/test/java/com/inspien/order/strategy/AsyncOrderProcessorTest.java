package com.inspien.order.strategy;

import com.inspien.common.exception.CustomException;
import com.inspien.common.exception.ErrorCode;
import com.inspien.mapper.OrderDomainMapper;
import com.inspien.order.domain.Order;
import com.inspien.order.domain.Outbox;
import com.inspien.order.service.OrderCommandService;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.sender.dto.CreateOrderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncOrderProcessorTest {

    @InjectMocks
    private AsyncOrderProcessor asyncOrderProcessor;

    @Mock private OrderCommandService orderCommandService;
    @Mock private OutboxRepository outboxRepository;
    @Mock private OrderDomainMapper orderDomainMapper;

    @Test
    @DisplayName("Outbox 저장 성공 및 SFTP 미호출 확인")
    void process_Success_SavesOutbox() {
        List<Order> orders = List.of(Order.builder().userId("user1").applicantKey("key").build());

        when(orderDomainMapper.toOutbox(any(Order.class))).thenReturn(new Outbox());
        when(outboxRepository.batchInsert(anyList())).thenReturn(new int[]{1});
        when(orderCommandService.saveOrdersWithRetry(any(), anyInt(), any()))
                .thenAnswer(invocation -> {
                    Consumer<List<Order>> action = invocation.getArgument(2);
                    action.accept(orders);
                    return CreateOrderResult.ok(1, 0, 1);
                });

        CreateOrderResult result = asyncOrderProcessor.process(orders, 0);

        assertTrue(result.success());
        assertEquals(1, result.orderCount());
        verify(outboxRepository, times(1)).batchInsert(anyList());
    }

    @Test
    @DisplayName("빈 주문 목록 시 VALIDATION_ERROR 예외 발생")
    void process_EmptyOrders_ThrowsValidationError() {
        CustomException ex = assertThrows(CustomException.class,
                () -> asyncOrderProcessor.process(List.of(), 0));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }
}
