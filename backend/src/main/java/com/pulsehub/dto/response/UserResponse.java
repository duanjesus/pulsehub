package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        String avatarUrl,
        UserStatus status,
        LocalDateTime lastSeenAt
) {
}
