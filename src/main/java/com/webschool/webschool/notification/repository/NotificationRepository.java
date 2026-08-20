package com.webschool.webschool.notification.repository;

import com.webschool.webschool.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipient_IdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipient_IdAndReadFalse(Long recipientId);

    List<Notification> findByRecipient_IdAndReadFalse(Long recipientId);
}
