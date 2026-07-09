package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateDirectConversationRequest(
        @NotNull(message = "otherUserId is required")
        Long otherUserId
) {
}
