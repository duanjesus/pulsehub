package com.pulsehub.service;

import com.pulsehub.entity.Conversation;
import com.pulsehub.exception.BusinessException;
import com.pulsehub.mapper.MessageMapper;
import com.pulsehub.mapper.UserMapper;
import com.pulsehub.repository.ConversationRepository;
import com.pulsehub.repository.MessageRepository;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Test
    void getOrCreateConversation_ordersUserIdsRegardlessOfCallerOrder() {
        when(conversationRepository.findByUserOneIdAndUserTwoId(5L, 9L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation conversation = conversationService.getOrCreateConversation(9L, 5L);

        assertThat(conversation.getUserOneId()).isEqualTo(5L);
        assertThat(conversation.getUserTwoId()).isEqualTo(9L);
        verify(conversationRepository).findByUserOneIdAndUserTwoId(5L, 9L);
    }

    @Test
    void getOrCreateConversation_returnsExistingPairWithoutSaving() {
        Conversation existing = Conversation.builder().id(42L).userOneId(5L).userTwoId(9L).build();
        when(conversationRepository.findByUserOneIdAndUserTwoId(5L, 9L)).thenReturn(Optional.of(existing));

        Conversation conversation = conversationService.getOrCreateConversation(5L, 9L);

        assertThat(conversation.getId()).isEqualTo(42L);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreateConversation_rejectsSelfConversation() {
        assertThatThrownBy(() -> conversationService.getOrCreateConversation(1L, 1L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(conversationRepository);
    }

}
