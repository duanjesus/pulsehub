package com.pulsehub.service;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.ParticipantResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConversationService {

    Conversation getOrCreateDirectConversation(Long userId, Long otherUserId);

    Conversation createGroupConversation(Long creatorId, String name, List<Long> memberIds);

    void addMember(Long conversationId, Long actingUserId, Long newMemberId);

    void removeMember(Long conversationId, Long actingUserId, Long memberId);

    void leaveConversation(Long conversationId, Long userId);

    List<ConversationResponse> listConversationsForUser(Long userId);

    ConversationResponse getConversation(Long conversationId, Long callerId);

    Page<MessageResponse> getMessages(Long conversationId, Long requesterId, Pageable pageable);

    void markAsRead(Long conversationId, Long readerId);

    List<ParticipantResponse> getParticipants(Long conversationId, Long requesterId);

    /** Active participants (including the caller, if still active) — used by the WS layer to fan out sends. */
    List<User> getActiveParticipants(Long conversationId);

    /** Throws if {@code userId} is not a currently-active participant of the conversation. */
    void assertActiveParticipant(Long conversationId, Long userId);
}
