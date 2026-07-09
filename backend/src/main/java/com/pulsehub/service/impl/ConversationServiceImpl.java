package com.pulsehub.service.impl;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.ReadReceiptEvent;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import com.pulsehub.exception.BusinessException;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.mapper.MessageMapper;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.ConversationRepository;
import com.pulsehub.repository.MessageRepository;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Conversation getOrCreateConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new BusinessException("Cannot start a conversation with yourself");
        }

        Long lower = Math.min(userId, otherUserId);
        Long higher = Math.max(userId, otherUserId);

        return conversationRepository.findByUserOneIdAndUserTwoId(lower, higher)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().userOneId(lower).userTwoId(higher).build()));
    }

    @Override
    public List<ConversationResponse> listConversationsForUser(Long userId) {
        List<Conversation> conversations = conversationRepository.findByUserOneIdOrUserTwoIdOrderByIdDesc(userId, userId);

        List<Long> otherUserIds = conversations.stream().map(c -> c.otherParticipant(userId)).toList();
        Map<Long, User> usersById = userRepository.findAllById(otherUserIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        return conversations.stream()
                .map(conversation -> {
                    Long otherId = conversation.otherParticipant(userId);
                    User other = usersById.get(otherId);
                    if (other == null) {
                        return null;
                    }
                    MessageResponse lastMessage = messageRepository
                            .findFirstByConversationIdOrderBySentAtDesc(conversation.getId())
                            .map(messageMapper::toResponse)
                            .orElse(null);
                    long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), userId);

                    return new ConversationResponse(conversation.getId(), userMapper.toResponse(other), lastMessage, unread);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public Page<MessageResponse> getMessages(Long conversationId, Long requesterId, Pageable pageable) {
        Conversation conversation = getParticipatingConversation(conversationId, requesterId);
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversation.getId(), pageable)
                .map(messageMapper::toResponse);
    }

    @Override
    @Transactional
    public void markAsRead(Long conversationId, Long readerId) {
        Conversation conversation = getParticipatingConversation(conversationId, readerId);
        int updated = messageRepository.markConversationAsRead(conversationId, readerId);

        if (updated > 0) {
            Long senderId = conversation.otherParticipant(readerId);
            userRepository.findById(senderId).ifPresent(sender -> messagingTemplate.convertAndSendToUser(
                    sender.getEmail(), "/queue/read-receipts",
                    new ReadReceiptEvent(conversationId, readerId, LocalDateTime.now())));
        }
    }

    private Conversation getParticipatingConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        if (!conversation.hasParticipant(userId)) {
            throw new AccessDeniedException("You are not a participant of this conversation");
        }

        return conversation;
    }

}
