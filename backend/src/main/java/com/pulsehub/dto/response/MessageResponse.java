package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        MessageType type,
        String content,
        String attachmentUrl,
        Integer attachmentDurationSeconds,
        LocalDateTime sentAt,
        /** User ids (excluding the sender) who have read this message so far. */
        List<Long> readBy
) {
}
