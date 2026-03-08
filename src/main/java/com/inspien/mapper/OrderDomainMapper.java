package com.inspien.mapper;

import com.inspien.order.domain.Order;
import com.inspien.order.domain.Outbox;
import com.inspien.receiver.jdbc.dto.PendingOrderRow;
import com.inspien.receiver.jdbc.dto.ShipmentRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderDomainMapper {

    @Mapping(target = "processed", constant = "false")
    @Mapping(target = "status", constant = "UNPROCESSED")
    @Mapping(target = "retryCount", constant = "0")
    @Mapping(target = "updated", expression = "java(java.time.LocalDateTime.now())")
    Outbox toOutbox(Order order);

    @Mapping(target = "shipmentId", source = "orderId")
    ShipmentRow toShipmentRow(Order order);

    Order toOrder(Outbox outbox);

    @Mapping(target = "shipmentId", source = "orderId")
    ShipmentRow toShipmentRow(PendingOrderRow row);
}
