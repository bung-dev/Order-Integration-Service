package com.inspien.order.service;

import com.inspien.common.config.properties.AppProperties;
import com.inspien.common.exception.ErrorCode;
import com.inspien.order.domain.Order;
import com.inspien.receiver.jdbc.BatchResult;
import com.inspien.receiver.jdbc.OrderRepository;
import com.inspien.sender.dto.CreateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final IdGenerator idGenerator;
    private final AppProperties appProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult saveOrdersWithId(List<Order> orders) {
        for (Order o : orders) {
            o.setOrderId(idGenerator.generate());
        }
        int[] result = orderRepository.batchInsert(orders);
        return BatchResult.from(result, orders.size());
    }

    public CreateOrderResult saveOrdersWithRetry(List<Order> orders, int skippedCount,
                                                  Consumer<List<Order>> postSaveAction) {
        int maxRetry = appProperties.getMaxRetry();
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                BatchResult summary = saveOrdersWithId(orders);
                log.info("[ORDER:DB] insert_done attempt={} total={} success={} fail={}",
                        attempt, summary.totalCount(), summary.successCount(), summary.failCount());
                if (postSaveAction != null) {
                    postSaveAction.accept(orders);
                }
                return CreateOrderResult.ok(summary.successCount(), skippedCount, attempt);
            } catch (DuplicateKeyException e) {
                if (attempt == maxRetry) throw e;
            }
        }
        throw ErrorCode.INTERNAL_ERROR.exception();
    }
}
