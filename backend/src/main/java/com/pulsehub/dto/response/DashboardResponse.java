package com.pulsehub.dto.response;

import java.util.List;

public record DashboardResponse(
        long onlineUsersCount,
        List<UserResponse> onlineUsers,
        List<ConversationResponse> recentConversations,
        long unreadMessagesCount,
        List<NotificationResponse> recentNotifications,
        long unreadNotificationsCount
) {
}
