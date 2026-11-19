package com.mochuong.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID orderId,
        String recipientEmail,
        String eventType,
        String message,
        Instant createdAt
) {
}
