package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotNull(message = "userId is required")
        Long userId
) {
}
