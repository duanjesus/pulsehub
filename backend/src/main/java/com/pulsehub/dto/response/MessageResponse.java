package com.pulsehub.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String content,
        LocalDateTime sentAt,
        /** User ids (excluding the sender) who have read this message so far. */
        List<Long> readBy
) {
}
