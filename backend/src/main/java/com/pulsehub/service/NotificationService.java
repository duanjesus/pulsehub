package com.pulsehub.service;

import com.pulsehub.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /** Persists a notification for {@code recipientId} and pushes it over their WS session. */
    void notifyNewMessage(Long recipientId, String senderName, String messageContent, Long conversationId);

    Page<NotificationResponse> listForUser(Long userId, Pageable pageable);

    long countUnread(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);
}
