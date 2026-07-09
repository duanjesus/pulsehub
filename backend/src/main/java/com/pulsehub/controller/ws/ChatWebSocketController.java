package com.pulsehub.controller.ws;

import com.pulsehub.dto.request.SendMessageRequest;
import com.pulsehub.dto.request.TypingRequest;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.TypingEvent;
import com.pulsehub.entity.Message;
import com.pulsehub.entity.User;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageService;
import com.pulsehub.service.NotificationService;
import com.pulsehub.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

/**
 * STOMP destinations under {@code /app/chat.*}. Each authenticated session has
 * a {@link Principal} (its email) attached by
 * {@link com.pulsehub.security.WebSocketAuthChannelInterceptor} on CONNECT.
 * Both direct and group conversations are handled identically here — a
 * message/typing event is always sent to every currently-active participant's
 * {@code /user/queue/*}, never broadcast to a shared conversation topic, so a
 * removed/left member stops receiving anything immediately.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PresenceService presenceService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());
        conversationService.assertActiveParticipant(request.conversationId(), sender.getId());

        presenceService.recordActivity(principal.getName());

        Message message = messageService.saveMessage(request.conversationId(), sender.getId(), request.content());
        MessageResponse response = messageService.toResponse(message, List.of());

        List<User> participants = conversationService.getActiveParticipants(request.conversationId());
        participants.forEach(user -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/messages", response));

        participants.stream()
                .filter(user -> !user.getId().equals(sender.getId()))
                .forEach(recipient -> notificationService.notifyNewMessage(
                        recipient.getId(), sender.getName(), request.content(), request.conversationId()));
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());
        conversationService.assertActiveParticipant(request.conversationId(), sender.getId());

        TypingEvent event = new TypingEvent(request.conversationId(), sender.getId(), request.typing());

        conversationService.getActiveParticipants(request.conversationId()).stream()
                .filter(user -> !user.getId().equals(sender.getId()))
                .forEach(user -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/typing", event));
    }

}
