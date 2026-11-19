package com.mochuong.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mochuong.notification.domain.NotificationRecord;
import com.mochuong.notification.domain.ProcessedEvent;
import com.mochuong.notification.dto.OrderEventPayload;
import com.mochuong.notification.repository.NotificationRepository;
import com.mochuong.notification.repository.ProcessedEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent Kafka consumer for order lifecycle events (order.created, order.cancelled).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(
            ProcessedEventRepository processedEventRepository,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.created", groupId = "notification-group")
    @Transactional
    public void onOrderCreated(String rawPayload) {
        handleEvent("order.created", rawPayload);
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-group")
    @Transactional
    public void onOrderCancelled(String rawPayload) {
        handleEvent("order.cancelled", rawPayload);
    }

    public void handleEvent(String topic, String rawPayload) {
        OrderEventPayload event;
        try {
            event = objectMapper.readValue(rawPayload, OrderEventPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON payload on topic " + topic, ex);
        }

        if (event.eventId() == null) {
            throw new IllegalArgumentException("Event payload missing required eventId");
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Duplicate Kafka event {} on topic {} ignored (idempotent)", event.eventId(), topic);
            return;
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), topic, Instant.now()));

        String message = buildNotificationMessage(topic, event);
        NotificationRecord record = NotificationRecord.create(
                event.orderId(),
                event.customerEmail(),
                topic,
                message
        );
        notificationRepository.save(record);
        log.info("Recorded {} notification for order {} (recipient={})",
                topic, event.orderId(), event.customerEmail());
    }

    private String buildNotificationMessage(String topic, OrderEventPayload event) {
        if ("order.created".equals(topic)) {
            return "Moc Huong Shop: Don hang " + event.orderId()
                    + " da duoc xac nhan thanh cong. Tong gia tri: " + event.totalAmount() + " VND.";
        }
        String reason = (event.reason() != null && !event.reason().isBlank()) ? event.reason() : "Khong xac dinh";
        return "Moc Huong Shop: Don hang " + event.orderId()
                + " da bi huy. Ly do: " + reason;
    }
}
