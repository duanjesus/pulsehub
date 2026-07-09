package com.pulsehub.service.impl;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.ParticipantResponse;
import com.pulsehub.dto.response.ReadReceiptEvent;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.ConversationParticipant;
import com.pulsehub.entity.MessageRead;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.ConversationType;
import com.pulsehub.entity.enums.ParticipantRole;
import com.pulsehub.exception.BusinessException;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.ConversationParticipantRepository;
import com.pulsehub.repository.ConversationRepository;
import com.pulsehub.repository.MessageReadRepository;
import com.pulsehub.repository.MessageRepository;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Conversation getOrCreateDirectConversation(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            throw new BusinessException("Cannot start a conversation with yourself");
        }

        String directKey = directKey(userId, otherUserId);

        return conversationRepository.findByDirectKey(directKey)
                .orElseGet(() -> {
                    Conversation conversation = conversationRepository.save(Conversation.builder()
                            .type(ConversationType.DIRECT)
                            .directKey(directKey)
                            .createdBy(userId)
                            .build());

                    participantRepository.save(newParticipant(conversation.getId(), userId, ParticipantRole.MEMBER));
                    participantRepository.save(newParticipant(conversation.getId(), otherUserId, ParticipantRole.MEMBER));

                    return conversation;
                });
    }

    @Override
    @Transactional
    public Conversation createGroupConversation(Long creatorId, String name, List<Long> memberIds) {
        List<Long> distinctMemberIds = memberIds.stream().distinct().filter(id -> !id.equals(creatorId)).toList();

        if (userRepository.findAllById(distinctMemberIds).size() != distinctMemberIds.size()) {
            throw new ResourceNotFoundException("One or more members were not found");
        }

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name(name)
                .createdBy(creatorId)
                .build());

        participantRepository.save(newParticipant(conversation.getId(), creatorId, ParticipantRole.OWNER));
        distinctMemberIds.forEach(memberId ->
                participantRepository.save(newParticipant(conversation.getId(), memberId, ParticipantRole.MEMBER)));

        return conversation;
    }

    @Override
    @Transactional
    public void addMember(Long conversationId, Long actingUserId, Long newMemberId) {
        Conversation conversation = requireGroupConversation(conversationId);
        requireOwner(conversation.getId(), actingUserId);

        if (!userRepository.existsById(newMemberId)) {
            throw new ResourceNotFoundException("User not found: " + newMemberId);
        }

        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversation.getId(), newMemberId)
                .orElse(null);

        if (participant != null) {
            throw new BusinessException("User is already a member of this group");
        }

        participantRepository.save(newParticipant(conversation.getId(), newMemberId, ParticipantRole.MEMBER));
    }

    @Override
    @Transactional
    public void removeMember(Long conversationId, Long actingUserId, Long memberId) {
        Conversation conversation = requireGroupConversation(conversationId);
        requireOwner(conversation.getId(), actingUserId);

        if (actingUserId.equals(memberId)) {
            throw new BusinessException("Use the leave endpoint to remove yourself");
        }

        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversation.getId(), memberId)
                .orElseThrow(() -> new ResourceNotFoundException("This user is not a member of the group"));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
    }

    @Override
    @Transactional
    public void leaveConversation(Long conversationId, Long userId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You are not a participant of this conversation"));

        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);

        if (participant.isOwner()) {
            participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(conversationId).stream()
                    .findFirst()
                    .ifPresent(successor -> {
                        successor.setRole(ParticipantRole.OWNER);
                        participantRepository.save(successor);
                    });
        }
    }

    @Override
    public List<ConversationResponse> listConversationsForUser(Long userId) {
        List<Long> conversationIds = participantRepository.findActiveConversationIdsForUser(userId);
        if (conversationIds.isEmpty()) {
            return List.of();
        }

        List<Conversation> conversations = conversationRepository.findByIdIn(conversationIds).stream()
                .sorted(Comparator.comparing(Conversation::getId).reversed())
                .toList();

        List<ConversationParticipant> allParticipants =
                participantRepository.findByConversationIdInAndLeftAtIsNull(conversationIds);

        List<Long> userIds = allParticipants.stream().map(ConversationParticipant::getUserId).distinct().toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<ConversationParticipant>> participantsByConversation =
                allParticipants.stream().collect(Collectors.groupingBy(ConversationParticipant::getConversationId));

        return conversations.stream()
                .map(conversation -> toConversationResponse(conversation, userId,
                        participantsByConversation.getOrDefault(conversation.getId(), List.of()), usersById))
                .toList();
    }

    @Override
    public ConversationResponse getConversation(Long conversationId, Long callerId) {
        assertActiveParticipant(conversationId, callerId);

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        List<ConversationParticipant> participants = participantRepository.findByConversationIdAndLeftAtIsNull(conversationId);
        Map<Long, User> usersById = userRepository.findAllById(participants.stream().map(ConversationParticipant::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return toConversationResponse(conversation, callerId, participants, usersById);
    }

    @Override
    public Page<MessageResponse> getMessages(Long conversationId, Long requesterId, Pageable pageable) {
        assertActiveParticipant(conversationId, requesterId);

        Page<com.pulsehub.entity.Message> page =
                messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable);

        Map<Long, List<Long>> readersByMessage = readersByMessageId(
                page.getContent().stream().map(com.pulsehub.entity.Message::getId).toList());

        return page.map(message ->
                messageService.toResponse(message, readersByMessage.getOrDefault(message.getId(), List.of())));
    }

    @Override
    @Transactional
    public void markAsRead(Long conversationId, Long readerId) {
        assertActiveParticipant(conversationId, readerId);

        List<Long> unreadIds = messageRepository.findUnreadMessageIds(conversationId, readerId);
        if (unreadIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        messageReadRepository.saveAll(unreadIds.stream()
                .map(messageId -> MessageRead.builder().messageId(messageId).userId(readerId).readAt(now).build())
                .toList());

        List<User> others = getActiveParticipants(conversationId).stream()
                .filter(u -> !u.getId().equals(readerId))
                .toList();

        ReadReceiptEvent event = new ReadReceiptEvent(conversationId, readerId, now);
        others.forEach(user -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/read-receipts", event));
    }

    @Override
    public List<ParticipantResponse> getParticipants(Long conversationId, Long requesterId) {
        assertActiveParticipant(conversationId, requesterId);

        List<ConversationParticipant> participants = participantRepository.findByConversationIdAndLeftAtIsNull(conversationId);
        Map<Long, User> usersById = userRepository.findAllById(participants.stream().map(ConversationParticipant::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return participants.stream()
                .map(p -> toParticipantResponse(p, usersById.get(p.getUserId())))
                .toList();
    }

    @Override
    public List<User> getActiveParticipants(Long conversationId) {
        List<Long> userIds = participantRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .toList();
        return userRepository.findAllById(userIds);
    }

    @Override
    public void assertActiveParticipant(Long conversationId, Long userId) {
        if (!participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new AccessDeniedException("You are not a participant of this conversation");
        }
    }

    private String directKey(Long userId, Long otherUserId) {
        long lower = Math.min(userId, otherUserId);
        long higher = Math.max(userId, otherUserId);
        return lower + "_" + higher;
    }

    private Conversation requireGroupConversation(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        if (conversation.isDirect()) {
            throw new BusinessException("This operation is only available for group conversations");
        }
        return conversation;
    }

    private void requireOwner(Long conversationId, Long userId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a participant of this conversation"));

        if (!participant.isOwner()) {
            throw new AccessDeniedException("Only the group owner can do this");
        }
    }

    private ConversationParticipant newParticipant(Long conversationId, Long userId, ParticipantRole role) {
        return ConversationParticipant.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build();
    }

    private Map<Long, List<Long>> readersByMessageId(List<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return messageReadRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(MessageRead::getMessageId,
                        Collectors.mapping(MessageRead::getUserId, Collectors.toList())));
    }

    private ConversationResponse toConversationResponse(Conversation conversation, Long callerId,
                                                          List<ConversationParticipant> participants,
                                                          Map<Long, User> usersById) {
        List<ParticipantResponse> participantResponses = participants.stream()
                .map(p -> toParticipantResponse(p, usersById.get(p.getUserId())))
                .filter(java.util.Objects::nonNull)
                .toList();

        String name;
        String avatarUrl;
        if (conversation.isDirect()) {
            ParticipantResponse other = participantResponses.stream()
                    .filter(p -> !p.userId().equals(callerId))
                    .findFirst()
                    .orElse(null);
            name = other != null ? other.name() : "Direct message";
            avatarUrl = other != null ? other.avatarUrl() : null;
        } else {
            name = conversation.getName();
            avatarUrl = null;
        }

        MessageResponse lastMessage = messageRepository.findFirstByConversationIdOrderBySentAtDesc(conversation.getId())
                .map(message -> messageService.toResponse(message,
                        readersByMessageId(List.of(message.getId())).getOrDefault(message.getId(), List.of())))
                .orElse(null);

        long unreadCount = messageRepository.countUnreadForUser(conversation.getId(), callerId);

        return new ConversationResponse(conversation.getId(), conversation.getType(), name, avatarUrl,
                participantResponses, lastMessage, unreadCount);
    }

    private ParticipantResponse toParticipantResponse(ConversationParticipant participant, User user) {
        if (user == null) {
            return null;
        }
        return new ParticipantResponse(user.getId(), user.getName(), user.getAvatarUrl(), user.getStatus(), participant.getRole());
    }

}
