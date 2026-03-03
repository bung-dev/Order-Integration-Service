package com.inspien.order.service;

import com.inspien.order.domain.Order;
import com.inspien.receiver.jdbc.BatchResult;
import com.inspien.receiver.jdbc.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final IdGenerator idGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult saveOrdersWithId(List<Order> orders) {
        for (Order o : orders) {
            o.setOrderId(idGenerator.generate());
        }
        int[] result = orderRepository.batchInsert(orders);
        return BatchResult.from(result, orders.size());
    }
}
