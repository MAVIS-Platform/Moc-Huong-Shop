package com.mochuong.order.service;

import com.mochuong.order.client.ProductSnapshotDto;
import com.mochuong.order.domain.Order;
import com.mochuong.order.domain.OrderItem;
import com.mochuong.order.domain.OrderStatus;
import com.mochuong.order.dto.CreateOrderRequest;
import com.mochuong.order.dto.OrderItemRequest;
import com.mochuong.order.dto.OrderResponse;
import com.mochuong.order.exception.ForbiddenOperationException;
import com.mochuong.order.exception.OrderNotFoundException;
import com.mochuong.order.exception.OrderStateConflictException;
import com.mochuong.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates the Order Placement Saga with Semantic Lock (PENDING),
 * inventory reservation, reverse compensation on failure, and atomic Outbox recording.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderTransactionService orderTransactionService;
    private final CatalogIntegrationService catalogIntegrationService;
    private final OrderRepository orderRepository;

    public OrderSagaOrchestrator(
            OrderTransactionService orderTransactionService,
            CatalogIntegrationService catalogIntegrationService,
            OrderRepository orderRepository) {
        this.orderTransactionService = orderTransactionService;
        this.catalogIntegrationService = catalogIntegrationService;
        this.orderRepository = orderRepository;
    }

    public OrderResponse placeOrder(
            UUID customerId,
            String customerEmail,
            String idempotencyKey,
            CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        Optional<Order> existing = orderTransactionService.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Returning idempotent order {} for key {}", existing.get().getId(), idempotencyKey);
            return OrderMapper.toResponse(existing.get());
        }

        Order pendingOrder = Order.createPending(
                customerId,
                customerEmail,
                request.shippingAddress().trim(),
                idempotencyKey.trim()
        );

        for (OrderItemRequest itemReq : request.items()) {
            ProductSnapshotDto snapshot = catalogIntegrationService.fetchProduct(itemReq.productId());
            pendingOrder.addItem(snapshot.id(), snapshot.name(), snapshot.unitPrice(), itemReq.quantity());
        }

        // T1: Semantic Lock (PENDING)
        Order savedPending = orderTransactionService.savePendingOrder(pendingOrder);

        // T2: Reserve stock per item
        List<ReservedItem> reservedItems = new ArrayList<>();
        try {
            for (OrderItem item : savedPending.getItems()) {
                catalogIntegrationService.reserveStock(item.getProductId(), item.getQuantity());
                reservedItems.add(new ReservedItem(item.getProductId(), item.getQuantity()));
            }
        } catch (RuntimeException ex) {
            log.warn("Saga reservation failed for order {}: {}. Executing compensation.",
                    savedPending.getId(), ex.getMessage());
            compensateReservations(reservedItems);
            orderTransactionService.cancelOrderWithOutbox(savedPending.getId(), ex.getMessage());
            throw ex;
        }

        // T3: Pivot transaction (CONFIRMED + Outbox order.created)
        Order confirmed = orderTransactionService.confirmOrderWithOutbox(savedPending.getId());
        return OrderMapper.toResponse(confirmed);
    }

    public OrderResponse cancelOrder(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isPrivileged = "ADMIN".equalsIgnoreCase(requesterRole) || "STAFF".equalsIgnoreCase(requesterRole);
        if (!isPrivileged && !order.getCustomerId().equals(requesterId)) {
            throw new ForbiddenOperationException("You are not authorized to cancel this order");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderStateConflictException("Order is already cancelled: " + orderId);
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                catalogIntegrationService.releaseStock(item.getProductId(), item.getQuantity());
            }
        }

        Order cancelled = orderTransactionService.cancelOrderWithOutbox(orderId, "Cancelled by requester");
        return OrderMapper.toResponse(cancelled);
    }

    private void compensateReservations(List<ReservedItem> reservedItems) {
        List<ReservedItem> reversed = new ArrayList<>(reservedItems);
        Collections.reverse(reversed);
        for (ReservedItem reserved : reversed) {
            try {
                catalogIntegrationService.releaseStock(reserved.productId(), reserved.quantity());
            } catch (RuntimeException releaseEx) {
                log.error("Compensation releaseStock failed for productId={}, quantity={}: {}",
                        reserved.productId(), reserved.quantity(), releaseEx.getMessage());
            }
        }
    }

    private record ReservedItem(Long productId, int quantity) {
    }
}
