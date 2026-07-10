package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(
        @NotBlank(message = "endpoint is required")
        String endpoint
) {
}
