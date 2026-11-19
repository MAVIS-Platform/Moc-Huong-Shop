package com.mochuong.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEventPayload(
        UUID eventId,
        UUID orderId,
        String customerEmail,
        BigDecimal totalAmount,
        String status,
        String reason,
        Instant occurredAt
) {
}
