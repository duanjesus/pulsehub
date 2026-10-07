package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.MessageType;
import com.pulsehub.service.impl.MessageDispatchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDispatchServiceImplTest {

    @Mock
    private MessageService messageService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RealtimeMessenger realtimeMessenger;

    @InjectMocks
    private MessageDispatchServiceImpl messageDispatchService;

    @Test
    void dispatchTextMessage_broadcastsToEveryParticipantAndNotifiesEveryoneElse() {
        Message message = Message.builder().id(1L).conversationId(10L).senderId(1L).type(MessageType.TEXT).content("hi").build();
        MessageResponse response = new MessageResponse(1L, 10L, 1L, MessageType.TEXT, "hi", null, null, null, List.of());

        User sender = User.builder().id(1L).name("Ada").email("ada@pulsehub.dev").build();
        User grace = User.builder().id(2L).name("Grace").email("grace@pulsehub.dev").build();
        User carol = User.builder().id(3L).name("Carol").email("carol@pulsehub.dev").build();

        when(messageService.saveTextMessage(10L, 1L, "hi")).thenReturn(message);
        when(messageService.toResponse(message, List.of())).thenReturn(response);
        when(conversationService.getActiveParticipants(10L)).thenReturn(List.of(sender, grace, carol));

        MessageResponse result = messageDispatchService.dispatchTextMessage(10L, 1L, "hi");

        assertThat(result).isEqualTo(response);
        verify(realtimeMessenger).sendToUser("ada@pulsehub.dev", "/queue/messages", response);
        verify(realtimeMessenger).sendToUser("grace@pulsehub.dev", "/queue/messages", response);
        verify(realtimeMessenger).sendToUser("carol@pulsehub.dev", "/queue/messages", response);

        verify(notificationService).notifyNewMessage(2L, "Ada", "hi", 10L);
        verify(notificationService).notifyNewMessage(3L, "Ada", "hi", 10L);
        verify(notificationService, never()).notifyNewMessage(eq(1L), any(), any(), any());
    }

    @Test
    void dispatchVoiceMessage_notifiesWithAFormattedDurationPreview() {
        Message message = Message.builder().id(2L).conversationId(10L).senderId(1L).type(MessageType.VOICE)
                .attachmentUrl("/uploads/voice/1-abc.webm").attachmentDurationSeconds(83).build();
        MessageResponse response = new MessageResponse(2L, 10L, 1L, MessageType.VOICE, null, "/uploads/voice/1-abc.webm", 83, null, List.of());

        User sender = User.builder().id(1L).name("Ada").email("ada@pulsehub.dev").build();
        User grace = User.builder().id(2L).name("Grace").email("grace@pulsehub.dev").build();

        when(messageService.saveVoiceMessage(10L, 1L, "/uploads/voice/1-abc.webm", 83)).thenReturn(message);
        when(messageService.toResponse(message, List.of())).thenReturn(response);
        when(conversationService.getActiveParticipants(10L)).thenReturn(List.of(sender, grace));

        messageDispatchService.dispatchVoiceMessage(10L, 1L, "/uploads/voice/1-abc.webm", 83);

        verify(notificationService).notifyNewMessage(eq(2L), eq("Ada"), eq("🎤 Voice message · 1:23"), eq(10L));
    }

}
