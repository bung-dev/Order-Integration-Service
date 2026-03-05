package com.inspien.order.strategy;

import com.inspien.common.exception.ErrorCode;
import com.inspien.mapper.OrderDomainMapper;
import com.inspien.order.domain.Order;
import com.inspien.order.domain.Outbox;
import com.inspien.order.service.OrderCommandService;
import com.inspien.receiver.jdbc.OutboxRepository;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service("asyncOrderProcessor")
@RequiredArgsConstructor
public class AsyncOrderProcessor implements OrderProcessor {

    private final OrderCommandService orderCommandService;
    private final OutboxRepository outboxRepository;
    private final OrderDomainMapper orderDomainMapper;

    @Override
    public CreateOrderResult process(List<Order> orders, int skippedCount) {
        if (orders == null || orders.isEmpty()) {
            throw ErrorCode.VALIDATION_ERROR.exception();
        }
        return orderCommandService.saveOrdersWithRetry(orders, skippedCount, savedOrders -> {
            List<Outbox> outboxes = savedOrders.stream()
                    .map(orderDomainMapper::toOutbox)
                    .toList();
            outboxRepository.batchInsert(outboxes);
        });
    }
}
