package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.MessageType;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.impl.SystemMessageServiceImpl;
import com.pulsehub.service.impl.SystemUserInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemMessageServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private MessageDispatchService messageDispatchService;

    @InjectMocks
    private SystemMessageServiceImpl systemMessageService;

    @Test
    void sendToUser_createsOrReusesDirectConversationAndDispatchesFromTheSystemBot() {
        User bot = User.builder().id(99L).name("PulseQueue Alerts").email(SystemUserInitializer.SYSTEM_BOT_EMAIL).build();
        Conversation conversation = Conversation.builder().id(10L).build();
        MessageResponse response = new MessageResponse(1L, 10L, 99L, MessageType.TEXT, "Sobra de arroz na Instituicao A", null, null, null, List.of());

        when(userRepository.findByEmail(SystemUserInitializer.SYSTEM_BOT_EMAIL)).thenReturn(Optional.of(bot));
        when(userRepository.existsById(5L)).thenReturn(true);
        when(conversationService.getOrCreateDirectConversation(99L, 5L)).thenReturn(conversation);
        when(messageDispatchService.dispatchTextMessage(10L, 99L, "Sobra de arroz na Instituicao A")).thenReturn(response);

        MessageResponse result = systemMessageService.sendToUser(5L, "Sobra de arroz na Instituicao A");

        assertThat(result).isEqualTo(response);
        verify(conversationService).getOrCreateDirectConversation(99L, 5L);
        verify(messageDispatchService).dispatchTextMessage(10L, 99L, "Sobra de arroz na Instituicao A");
    }

    @Test
    void sendToUser_throwsWhenTargetUserDoesNotExist() {
        User bot = User.builder().id(99L).name("PulseQueue Alerts").email(SystemUserInitializer.SYSTEM_BOT_EMAIL).build();
        when(userRepository.findByEmail(SystemUserInitializer.SYSTEM_BOT_EMAIL)).thenReturn(Optional.of(bot));
        when(userRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> systemMessageService.sendToUser(404L, "hi"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(conversationService, messageDispatchService);
    }

    @Test
    void sendToUser_throwsWhenSystemBotUserIsMissing() {
        when(userRepository.findByEmail(SystemUserInitializer.SYSTEM_BOT_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemMessageService.sendToUser(5L, "hi"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(conversationService, messageDispatchService);
    }
}
