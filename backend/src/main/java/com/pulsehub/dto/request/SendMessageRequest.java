package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload sent over STOMP to {@code /app/chat.send}. The conversation must
 * already exist — for a new 1:1 chat, the client first calls
 * {@code POST /api/v1/conversations/direct} to get-or-create it.
 */
public record SendMessageRequest(
        @NotNull(message = "conversationId is required")
        Long conversationId,

        @NotBlank(message = "Content is required")
        @Size(max = 4000, message = "Content must be at most 4000 characters")
        String content
) {
}
