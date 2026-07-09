package com.pulsehub.service;

import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.ConversationParticipant;
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
import com.pulsehub.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReadRepository messageReadRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageService messageService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Test
    void getOrCreateDirectConversation_dedupesByDirectKeyRegardlessOfCallerOrder() {
        when(conversationRepository.findByDirectKey("5_9")).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        Conversation conversation = conversationService.getOrCreateDirectConversation(9L, 5L);

        assertThat(conversation.getDirectKey()).isEqualTo("5_9");
        assertThat(conversation.getType()).isEqualTo(ConversationType.DIRECT);
        verify(conversationRepository).findByDirectKey("5_9");
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void getOrCreateDirectConversation_returnsExistingWithoutCreatingParticipants() {
        Conversation existing = Conversation.builder().id(42L).directKey("5_9").type(ConversationType.DIRECT).build();
        when(conversationRepository.findByDirectKey("5_9")).thenReturn(Optional.of(existing));

        Conversation conversation = conversationService.getOrCreateDirectConversation(5L, 9L);

        assertThat(conversation.getId()).isEqualTo(42L);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void getOrCreateDirectConversation_rejectsSelfConversation() {
        assertThatThrownBy(() -> conversationService.getOrCreateDirectConversation(1L, 1L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void createGroupConversation_createsOwnerAndMembers() {
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(
                User.builder().id(2L).build(), User.builder().id(3L).build()));
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        Conversation conversation = conversationService.createGroupConversation(1L, "Team", List.of(2L, 3L));

        assertThat(conversation.getType()).isEqualTo(ConversationType.GROUP);
        assertThat(conversation.getName()).isEqualTo("Team");

        var captor = org.mockito.ArgumentCaptor.forClass(ConversationParticipant.class);
        verify(participantRepository, times(3)).save(captor.capture());
        List<ConversationParticipant> saved = captor.getAllValues();
        assertThat(saved).anySatisfy(p -> {
            assertThat(p.getUserId()).isEqualTo(1L);
            assertThat(p.getRole()).isEqualTo(ParticipantRole.OWNER);
        });
        assertThat(saved).filteredOn(p -> p.getRole() == ParticipantRole.MEMBER).hasSize(2);
    }

    @Test
    void createGroupConversation_rejectsUnknownMembers() {
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of());

        assertThatThrownBy(() -> conversationService.createGroupConversation(1L, "Team", List.of(2L)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void addMember_rejectsWhenActingUserIsNotOwner() {
        Conversation group = Conversation.builder().id(10L).type(ConversationType.GROUP).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(group));
        when(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(ConversationParticipant.builder().role(ParticipantRole.MEMBER).build()));

        assertThatThrownBy(() -> conversationService.addMember(10L, 1L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addMember_rejectsDirectConversations() {
        Conversation direct = Conversation.builder().id(10L).type(ConversationType.DIRECT).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> conversationService.addMember(10L, 1L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removeMember_rejectsSelfRemoval() {
        Conversation group = Conversation.builder().id(10L).type(ConversationType.GROUP).build();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(group));
        when(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L))
                .thenReturn(Optional.of(ConversationParticipant.builder().role(ParticipantRole.OWNER).build()));

        assertThatThrownBy(() -> conversationService.removeMember(10L, 1L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void leaveConversation_promotesOldestRemainingMemberWhenOwnerLeaves() {
        ConversationParticipant owner = ConversationParticipant.builder()
                .id(1L).conversationId(10L).userId(1L).role(ParticipantRole.OWNER).build();
        ConversationParticipant successor = ConversationParticipant.builder()
                .id(2L).conversationId(10L).userId(2L).role(ParticipantRole.MEMBER).joinedAt(LocalDateTime.now()).build();

        when(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L)).thenReturn(Optional.of(owner));
        when(participantRepository.findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(10L)).thenReturn(List.of(successor));

        conversationService.leaveConversation(10L, 1L);

        assertThat(owner.getLeftAt()).isNotNull();
        assertThat(successor.getRole()).isEqualTo(ParticipantRole.OWNER);
        verify(participantRepository).save(owner);
        verify(participantRepository).save(successor);
    }

    @Test
    void leaveConversation_doesNotPromoteAnyoneWhenLeaverIsNotOwner() {
        ConversationParticipant member = ConversationParticipant.builder()
                .id(1L).conversationId(10L).userId(1L).role(ParticipantRole.MEMBER).build();
        when(participantRepository.findByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L)).thenReturn(Optional.of(member));

        conversationService.leaveConversation(10L, 1L);

        verify(participantRepository, never()).findByConversationIdAndLeftAtIsNullOrderByJoinedAtAsc(any());
    }

    @Test
    void markAsRead_broadcastsReadReceiptToEveryOtherActiveParticipant() {
        when(messageRepository.findUnreadMessageIds(10L, 1L)).thenReturn(List.of(100L, 101L));
        when(participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L)).thenReturn(true);
        when(participantRepository.findByConversationIdAndLeftAtIsNull(10L)).thenReturn(List.of(
                ConversationParticipant.builder().userId(1L).build(),
                ConversationParticipant.builder().userId(2L).build(),
                ConversationParticipant.builder().userId(3L).build()));
        when(userRepository.findAllById(anyList())).thenReturn(List.of(
                User.builder().id(1L).email("reader@pulsehub.dev").build(),
                User.builder().id(2L).email("bob@pulsehub.dev").build(),
                User.builder().id(3L).email("carol@pulsehub.dev").build()));

        conversationService.markAsRead(10L, 1L);

        verify(messageReadRepository).saveAll(anyList());
        verify(messagingTemplate).convertAndSendToUser(eq("bob@pulsehub.dev"), eq("/queue/read-receipts"), any());
        verify(messagingTemplate).convertAndSendToUser(eq("carol@pulsehub.dev"), eq("/queue/read-receipts"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("reader@pulsehub.dev"), eq("/queue/read-receipts"), any());
    }

    @Test
    void markAsRead_doesNothingWhenNothingWasUnread() {
        when(messageRepository.findUnreadMessageIds(10L, 1L)).thenReturn(List.of());
        when(participantRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(10L, 1L)).thenReturn(true);

        conversationService.markAsRead(10L, 1L);

        verifyNoInteractions(messageReadRepository, messagingTemplate);
    }

}
