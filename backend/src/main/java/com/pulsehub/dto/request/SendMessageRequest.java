package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload sent over STOMP to {@code /app/chat.send}. {@code recipientId} is
 * enough to identify (or lazily create) the 1:1 conversation — the client
 * never needs to know the conversation id up front.
 */
public record SendMessageRequest(
        @NotNull(message = "recipientId is required")
        Long recipientId,

        @NotBlank(message = "Content is required")
        @Size(max = 4000, message = "Content must be at most 4000 characters")
        String content
) {
}
