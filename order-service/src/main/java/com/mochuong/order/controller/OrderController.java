package com.mochuong.order.controller;

import com.mochuong.order.dto.CreateOrderRequest;
import com.mochuong.order.dto.OrderResponse;
import com.mochuong.order.service.OrderSagaOrchestrator;
import com.mochuong.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final OrderService orderService;

    public OrderController(OrderSagaOrchestrator orderSagaOrchestrator, OrderService orderService) {
        this.orderSagaOrchestrator = orderSagaOrchestrator;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader("X-User-Email") String customerEmail,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderSagaOrchestrator.placeOrder(customerId, customerEmail, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> getMyOrders(@RequestHeader("X-User-Id") UUID customerId) {
        return ResponseEntity.ok(orderService.getMyOrders(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        return ResponseEntity.ok(orderService.getOrderById(id, requesterId, requesterRole));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") UUID requesterId,
            @RequestHeader(value = "X-User-Role", required = false) String requesterRole) {
        return ResponseEntity.ok(orderSagaOrchestrator.cancelOrder(id, requesterId, requesterRole));
    }
}
