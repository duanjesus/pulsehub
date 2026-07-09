package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        Long relatedConversationId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
