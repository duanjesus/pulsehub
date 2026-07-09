package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotNull;

public record TypingRequest(
        @NotNull(message = "conversationId is required")
        Long conversationId,

        boolean typing
) {
}
