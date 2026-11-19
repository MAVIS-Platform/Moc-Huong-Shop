package com.mochuong.notification.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mochuong.notification.domain.NotificationRecord;
import com.mochuong.notification.domain.ProcessedEvent;
import com.mochuong.notification.dto.OrderEventPayload;
import com.mochuong.notification.repository.NotificationRepository;
import com.mochuong.notification.repository.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private ObjectMapper objectMapper;
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        consumer = new OrderEventConsumer(processedEventRepository, notificationRepository, objectMapper);
    }

    @Test
    void onOrderCreated_newEvent_savesProcessedEventAndNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEventPayload payload = new OrderEventPayload(
                eventId,
                orderId,
                "customer@mochuong.vn",
                new BigDecimal("700000.00"),
                "CONFIRMED",
                null,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(payload);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderCreated(json);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(orderId, captor.getValue().getOrderId());
        assertEquals("customer@mochuong.vn", captor.getValue().getRecipientEmail());
        assertEquals("order.created", captor.getValue().getEventType());
        assertTrue(captor.getValue().getMessage().contains("700000.00"));
    }

    @Test
    void onOrderCreated_duplicateEventId_skipsProcessingIdempotently() throws Exception {
        UUID eventId = UUID.randomUUID();
        OrderEventPayload payload = new OrderEventPayload(
                eventId,
                UUID.randomUUID(),
                "customer@mochuong.vn",
                new BigDecimal("350000.00"),
                "CONFIRMED",
                null,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(payload);

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onOrderCreated(json);

        verify(processedEventRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void onOrderCancelled_newEvent_savesCancellationNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderEventPayload payload = new OrderEventPayload(
                eventId,
                orderId,
                "customer@mochuong.vn",
                new BigDecimal("220000.00"),
                "CANCELLED",
                "Insufficient stock",
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(payload);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onOrderCancelled(json);

        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals("order.cancelled", captor.getValue().getEventType());
        assertTrue(captor.getValue().getMessage().contains("Insufficient stock"));
    }
}
