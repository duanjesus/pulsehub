package com.pulsehub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SystemMessageRequest(
        @NotNull Long targetUserId,
        @NotBlank @Size(max = 2000) String content
) {
}
