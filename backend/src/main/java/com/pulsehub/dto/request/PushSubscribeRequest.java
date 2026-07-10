package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PushSubscribeRequest(
        @NotBlank(message = "endpoint is required")
        String endpoint,

        @NotBlank(message = "p256dh is required")
        String p256dh,

        @NotBlank(message = "auth is required")
        String auth
) {
}
