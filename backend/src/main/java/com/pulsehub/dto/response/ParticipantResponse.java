package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.ParticipantRole;
import com.pulsehub.entity.enums.UserStatus;

public record ParticipantResponse(
        Long userId,
        String name,
        String avatarUrl,
        UserStatus status,
        ParticipantRole role
) {
}
