package com.inspien.order.strategy;

import com.inspien.order.domain.Order;
import com.inspien.sender.dto.CreateOrderResult;

import java.util.List;

public interface OrderProcessor {
    CreateOrderResult process(List<Order> orders, int skippedCount);
}
