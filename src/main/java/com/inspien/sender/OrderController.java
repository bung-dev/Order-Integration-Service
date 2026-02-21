package com.inspien.sender;

import com.inspien.order.service.OrderService;
import com.inspien.sender.dto.CreateOrderRequest;
import com.inspien.sender.dto.CreateOrderResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<CreateOrderResult> create(@RequestBody @Valid CreateOrderRequest request) {
        CreateOrderResult orderSync = orderService.createOrderSync(request.base64Xml());
        return ResponseEntity.ok(orderSync);
    }

    @PostMapping("/outbox")
    public ResponseEntity<CreateOrderResult> createOutBOx(@RequestBody @Valid CreateOrderRequest request) {
        CreateOrderResult orderSync = orderService.createOrderOutbox(request.base64Xml());
        return ResponseEntity.ok(orderSync);
    }
}
