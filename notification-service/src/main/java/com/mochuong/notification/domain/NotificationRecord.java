package com.mochuong.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationRecord {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationRecord() {
    }

    public NotificationRecord(UUID id, UUID orderId, String recipientEmail, String eventType, String message, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.recipientEmail = recipientEmail;
        this.eventType = eventType;
        this.message = message;
        this.createdAt = createdAt;
    }

    public static NotificationRecord create(UUID orderId, String recipientEmail, String eventType, String message) {
        return new NotificationRecord(UUID.randomUUID(), orderId, recipientEmail, eventType, message, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
