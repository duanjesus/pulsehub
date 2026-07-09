package com.pulsehub.dto.response;

public record ConversationResponse(
        Long id,
        UserResponse participant,
        MessageResponse lastMessage,
        long unreadCount
) {
}
