package com.inspien.order.service;

import com.inspien.mapper.OrderMapper;
import com.inspien.mapper.OrderParserXML;
import com.inspien.mapper.OrderRequestValidator;
import com.inspien.mapper.dto.FlattenResult;
import com.inspien.mapper.dto.OrderHeaderXml;
import com.inspien.mapper.dto.OrderItemXml;
import com.inspien.mapper.dto.OrderRequestXML;
import com.inspien.order.domain.Order;
import com.inspien.order.strategy.OrderProcessor;
import com.inspien.sender.dto.CreateOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private OrderService orderService;

    @Mock private OrderParserXML orderParserXML;
    @Mock private OrderRequestValidator validator;
    @Mock private OrderMapper mapper;
    @Mock private OrderProcessor syncOrderProcessor;
    @Mock private OrderProcessor asyncOrderProcessor;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderParserXML, validator, mapper, syncOrderProcessor, asyncOrderProcessor);
    }

    private OrderRequestXML buildRequest() {
        OrderRequestXML request = new OrderRequestXML();
        OrderHeaderXml h = new OrderHeaderXml();
        h.setUserId("user1"); h.setName("Name"); h.setAddress("Addr"); h.setStatus("N");
        request.setHeaders(List.of(h));
        OrderItemXml item = new OrderItemXml();
        item.setUserId("user1"); item.setItemId("I001"); item.setItemName("Item"); item.setPrice("1000");
        request.setItems(List.of(item));
        return request;
    }

    @Test
    @DisplayName("동기 주문 생성 시 syncOrderProcessor에 위임")
    void createOrderSync_DelegatesTo_SyncProcessor() throws Exception {
        String base64Xml = "dGVzdCB4bWw=";
        List<Order> orders = List.of(Order.builder().userId("user1").build());

        when(orderParserXML.parse(anyString())).thenReturn(buildRequest());
        when(mapper.flatten(any())).thenReturn(new FlattenResult(orders, 0));
        when(syncOrderProcessor.process(any(), anyInt())).thenReturn(CreateOrderResult.ok(1, 0, 1));

        CreateOrderResult result = orderService.createOrderSync(base64Xml);

        assertTrue(result.success());
        assertEquals(1, result.orderCount());
        verify(syncOrderProcessor).process(eq(orders), eq(0));
        verify(asyncOrderProcessor, never()).process(any(), anyInt());
    }

    @Test
    @DisplayName("아웃박스 주문 생성 시 asyncOrderProcessor에 위임")
    void createOrderOutbox_DelegatesTo_AsyncProcessor() throws Exception {
        String base64Xml = "dGVzdCB4bWw=";
        List<Order> orders = List.of(Order.builder().userId("user1").applicantKey("key").build());

        when(orderParserXML.parse(anyString())).thenReturn(buildRequest());
        when(mapper.flatten(any())).thenReturn(new FlattenResult(orders, 0));
        when(asyncOrderProcessor.process(any(), anyInt())).thenReturn(CreateOrderResult.ok(1, 0, 1));

        CreateOrderResult result = orderService.createOrderOutbox(base64Xml);

        assertTrue(result.success());
        assertEquals(1, result.orderCount());
        verify(asyncOrderProcessor).process(eq(orders), eq(0));
        verify(syncOrderProcessor, never()).process(any(), anyInt());
    }
}
