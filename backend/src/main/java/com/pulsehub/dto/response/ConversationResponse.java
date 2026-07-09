package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.ConversationType;

import java.util.List;

public record ConversationResponse(
        Long id,
        ConversationType type,
        /** Group name, or (for DIRECT) the other participant's display name. */
        String name,
        /** null for GROUP — the frontend falls back to a generic group icon. */
        String avatarUrl,
        List<ParticipantResponse> participants,
        MessageResponse lastMessage,
        long unreadCount
) {
}
