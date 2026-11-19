package com.mochuong.notification.service;

import com.mochuong.notification.dto.NotificationResponse;
import com.mochuong.notification.repository.NotificationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("X-User-Email header is required");
        }
        return notificationRepository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(email.trim()).stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getOrderId(),
                        n.getRecipientEmail(),
                        n.getEventType(),
                        n.getMessage(),
                        n.getCreatedAt()))
                .toList();
    }
}
