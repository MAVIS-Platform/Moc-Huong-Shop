package com.mochuong.notification.repository;

import com.mochuong.notification.domain.NotificationRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationRecord, UUID> {

    List<NotificationRecord> findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(String recipientEmail);
}
