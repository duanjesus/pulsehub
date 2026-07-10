package com.pulsehub.service.impl;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;
import com.pulsehub.entity.User;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageDispatchService;
import com.pulsehub.service.MessageService;
import com.pulsehub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageDispatchServiceImpl implements MessageDispatchService {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public MessageResponse dispatchTextMessage(Long conversationId, Long senderId, String content) {
        Message message = messageService.saveTextMessage(conversationId, senderId, content);
        return dispatch(message, senderId, content);
    }

    @Override
    public MessageResponse dispatchVoiceMessage(Long conversationId, Long senderId, String attachmentUrl, int durationSeconds) {
        Message message = messageService.saveVoiceMessage(conversationId, senderId, attachmentUrl, durationSeconds);
        return dispatch(message, senderId, "🎤 Voice message · " + formatDuration(durationSeconds));
    }

    private MessageResponse dispatch(Message message, Long senderId, String notificationPreview) {
        MessageResponse response = messageService.toResponse(message, List.of());
        List<User> participants = conversationService.getActiveParticipants(message.getConversationId());

        participants.forEach(user -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/messages", response));

        User sender = participants.stream()
                .filter(user -> user.getId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Sender is not an active participant"));

        participants.stream()
                .filter(user -> !user.getId().equals(senderId))
                .forEach(recipient -> notificationService.notifyNewMessage(
                        recipient.getId(), sender.getName(), notificationPreview, message.getConversationId()));

        return response;
    }

    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

}
