package com.pulsehub.dto.response;

/**
 * Sent privately to a single recipient's {@code /user/queue/typing} destination.
 */
public record TypingEvent(
        Long conversationId,
        Long senderId,
        boolean typing
) {
}
