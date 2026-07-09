package com.pulsehub.service.impl;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.DashboardResponse;
import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.DashboardService;
import com.pulsehub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_CONVERSATIONS_LIMIT = 5;
    private static final int RECENT_NOTIFICATIONS_LIMIT = 5;

    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final NotificationService notificationService;
    private final UserMapper userMapper;

    @Override
    public DashboardResponse getDashboard(Long userId) {
        List<UserResponse> onlineUsers = userRepository.findByIdNotOrderByNameAsc(userId).stream()
                .filter(u -> u.getStatus() != UserStatus.OFFLINE)
                .map(userMapper::toResponse)
                .toList();

        List<ConversationResponse> allConversations = conversationService.listConversationsForUser(userId);

        List<ConversationResponse> recentConversations = allConversations.stream()
                .limit(RECENT_CONVERSATIONS_LIMIT)
                .toList();

        long unreadMessagesCount = allConversations.stream()
                .mapToLong(ConversationResponse::unreadCount)
                .sum();

        var recentNotifications = notificationService
                .listForUser(userId, PageRequest.of(0, RECENT_NOTIFICATIONS_LIMIT))
                .getContent();
        long unreadNotificationsCount = notificationService.countUnread(userId);

        return new DashboardResponse(
                onlineUsers.size(), onlineUsers, recentConversations, unreadMessagesCount,
                recentNotifications, unreadNotificationsCount);
    }

}
