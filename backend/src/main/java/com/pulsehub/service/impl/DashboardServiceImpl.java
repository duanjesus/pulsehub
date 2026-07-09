package com.pulsehub.service.impl;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.DashboardResponse;
import com.pulsehub.dto.response.UserResponse;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_CONVERSATIONS_LIMIT = 5;

    private final UserRepository userRepository;
    private final ConversationService conversationService;
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

        return new DashboardResponse(onlineUsers.size(), onlineUsers, recentConversations, unreadMessagesCount);
    }

}
