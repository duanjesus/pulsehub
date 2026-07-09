package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotNull;

public record TypingRequest(
        @NotNull(message = "recipientId is required")
        Long recipientId,

        boolean typing
) {
}
