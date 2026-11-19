package com.mochuong.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mochuong.order.domain.Order;
import com.mochuong.order.domain.OutboxEvent;
import com.mochuong.order.dto.OrderEventPayload;
import com.mochuong.order.exception.OrderNotFoundException;
import com.mochuong.order.repository.OrderRepository;
import com.mochuong.order.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes short ACID database transactions for each Saga state transition and Outbox write.
 */
@Service
public class OrderTransactionService {

    public static final String TOPIC_ORDER_CREATED = "order.created";
    public static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderTransactionService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional
    public Order savePendingOrder(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public Order confirmOrderWithOutbox(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.confirm();
        Order saved = orderRepository.save(order);
        saveOutboxEvent(saved, TOPIC_ORDER_CREATED, null);
        return saved;
    }

    @Transactional
    public Order cancelOrderWithOutbox(UUID orderId, String reason) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel(reason);
        Order saved = orderRepository.save(order);
        saveOutboxEvent(saved, TOPIC_ORDER_CANCELLED, reason);
        return saved;
    }

    private void saveOutboxEvent(Order order, String topic, String reason) {
        UUID eventId = UUID.randomUUID();
        OrderEventPayload eventPayload = new OrderEventPayload(
                eventId,
                order.getId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getStatus().name(),
                reason,
                Instant.now()
        );
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox event payload for order " + order.getId(), ex);
        }
        OutboxEvent outboxEvent = OutboxEvent.pending(eventId, order.getId().toString(), topic, jsonPayload);
        outboxEventRepository.save(outboxEvent);
    }
}
