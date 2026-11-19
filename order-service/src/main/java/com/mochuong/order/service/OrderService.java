package com.mochuong.order.service;

import com.mochuong.order.domain.Order;
import com.mochuong.order.dto.OrderResponse;
import com.mochuong.order.exception.ForbiddenOperationException;
import com.mochuong.order.exception.OrderNotFoundException;
import com.mochuong.order.repository.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID requesterId, String requesterRole) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isPrivileged = "ADMIN".equalsIgnoreCase(requesterRole) || "STAFF".equalsIgnoreCase(requesterRole);
        if (!isPrivileged && !order.getCustomerId().equals(requesterId)) {
            throw new ForbiddenOperationException("You are not authorized to view this order");
        }
        return OrderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(UUID customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderMapper::toResponse)
                .toList();
    }
}
