package com.pulsehub.dto.response;

import java.time.LocalDateTime;

/**
 * Sent privately to the other participant's {@code /user/queue/read-receipts}
 * whenever a conversation is marked as read, so their sent messages can flip
 * to "read" immediately instead of waiting for the next fetch.
 */
public record ReadReceiptEvent(
        Long conversationId,
        Long readerId,
        LocalDateTime readAt
) {
}
