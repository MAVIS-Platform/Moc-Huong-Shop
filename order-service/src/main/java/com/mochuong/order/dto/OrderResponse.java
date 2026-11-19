package com.mochuong.order.dto;

import com.mochuong.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String customerEmail,
        String shippingAddress,
        OrderStatus status,
        BigDecimal totalAmount,
        String idempotencyKey,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
}
