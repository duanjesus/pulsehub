package com.pulsehub.service;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConversationService {

    Conversation getOrCreateConversation(Long userId, Long otherUserId);

    List<ConversationResponse> listConversationsForUser(Long userId);

    Page<MessageResponse> getMessages(Long conversationId, Long requesterId, Pageable pageable);

    void markAsRead(Long conversationId, Long readerId);
}
