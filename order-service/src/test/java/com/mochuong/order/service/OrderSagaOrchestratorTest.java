package com.mochuong.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mochuong.order.client.ProductSnapshotDto;
import com.mochuong.order.domain.Order;
import com.mochuong.order.domain.OrderStatus;
import com.mochuong.order.domain.OutboxEvent;
import com.mochuong.order.dto.CreateOrderRequest;
import com.mochuong.order.dto.OrderItemRequest;
import com.mochuong.order.dto.OrderResponse;
import com.mochuong.order.exception.InsufficientStockException;
import com.mochuong.order.repository.OrderRepository;
import com.mochuong.order.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private CatalogIntegrationService catalogIntegrationService;

    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OrderTransactionService txService = new OrderTransactionService(orderRepository, outboxEventRepository, objectMapper);
        orchestrator = new OrderSagaOrchestrator(txService, catalogIntegrationService, orderRepository);
    }

    @Test
    void placeOrder_happyPath_confirmsOrderAndWritesOrderCreatedOutbox() {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "idem-happy-001";
        CreateOrderRequest request = new CreateOrderRequest(
                "123 Nguyen Hue, Quan 1, TP.HCM",
                List.of(new OrderItemRequest(1L, 2))
        );

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(catalogIntegrationService.fetchProduct(1L)).thenReturn(
                new ProductSnapshotDto(1L, "MH-NHANG-01", "Nhang Tram Khong Tam", new BigDecimal("350000.00"), 50)
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findWithItemsById(any(UUID.class))).thenAnswer(inv -> {
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            return Optional.of(captor.getValue());
        });
        doNothing().when(catalogIntegrationService).reserveStock(1L, 2);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orchestrator.placeOrder(customerId, "customer@mochuong.vn", idempotencyKey, request);

        assertEquals(OrderStatus.CONFIRMED, response.status());
        assertEquals(new BigDecimal("700000.00"), response.totalAmount());
        verify(catalogIntegrationService).reserveStock(1L, 2);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertEquals("order.created", outboxCaptor.getValue().getTopic());
    }

    @Test
    void placeOrder_secondItemInsufficientStock_compensatesFirstItemAndCancelsOrder() {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "idem-fail-002";
        CreateOrderRequest request = new CreateOrderRequest(
                "45 Le Loi, Da Nang",
                List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 99))
        );

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(catalogIntegrationService.fetchProduct(1L)).thenReturn(
                new ProductSnapshotDto(1L, "MH-NHANG-01", "Nhang Tram Khong Tam", new BigDecimal("350000.00"), 50)
        );
        when(catalogIntegrationService.fetchProduct(2L)).thenReturn(
                new ProductSnapshotDto(2L, "MH-NU-01", "Tram Nu Que Lam", new BigDecimal("220000.00"), 10)
        );

        final Order[] persistedOrder = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            persistedOrder[0] = inv.getArgument(0);
            return persistedOrder[0];
        });
        when(orderRepository.findWithItemsById(any(UUID.class))).thenAnswer(inv -> Optional.of(persistedOrder[0]));

        doNothing().when(catalogIntegrationService).reserveStock(1L, 2);
        doThrow(new InsufficientStockException(2L, 99)).when(catalogIntegrationService).reserveStock(2L, 99);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(InsufficientStockException.class,
                () -> orchestrator.placeOrder(customerId, "customer@mochuong.vn", idempotencyKey, request));

        verify(catalogIntegrationService).releaseStock(1L, 2);
        verify(catalogIntegrationService, never()).releaseStock(2L, 99);
        assertNotNull(persistedOrder[0]);
        assertEquals(OrderStatus.CANCELLED, persistedOrder[0].getStatus());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertEquals("order.cancelled", outboxCaptor.getValue().getTopic());
    }

    @Test
    void placeOrder_duplicateIdempotencyKey_returnsExistingOrderWithoutCallingCatalog() {
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = "idem-dup-003";
        Order existingOrder = Order.createPending(customerId, "customer@mochuong.vn", "Ha Noi", idempotencyKey);
        existingOrder.addItem(1L, "Nhang Tram Khong Tam", new BigDecimal("350000.00"), 1);
        existingOrder.confirm();

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        OrderResponse response = orchestrator.placeOrder(
                customerId,
                "customer@mochuong.vn",
                idempotencyKey,
                new CreateOrderRequest("Ha Noi", List.of(new OrderItemRequest(1L, 1)))
        );

        assertEquals(existingOrder.getId(), response.id());
        assertEquals(OrderStatus.CONFIRMED, response.status());
        verify(catalogIntegrationService, never()).fetchProduct(any());
        verify(catalogIntegrationService, never()).reserveStock(any(), any(Integer.class));
    }
}
