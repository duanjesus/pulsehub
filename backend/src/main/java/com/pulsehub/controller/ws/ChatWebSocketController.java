package com.pulsehub.controller.ws;

import com.pulsehub.dto.request.SendMessageRequest;
import com.pulsehub.dto.request.TypingRequest;
import com.pulsehub.dto.response.TypingEvent;
import com.pulsehub.entity.User;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageDispatchService;
import com.pulsehub.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP destinations under {@code /app/chat.*}. Each authenticated session has
 * a {@link Principal} (its email) attached by
 * {@link com.pulsehub.security.WebSocketAuthChannelInterceptor} on CONNECT.
 * Both direct and group conversations are handled identically here — a
 * message/typing event is always sent to every currently-active participant's
 * {@code /user/queue/*}, never broadcast to a shared conversation topic, so a
 * removed/left member stops receiving anything immediately. The actual
 * persist+broadcast+notify work for a text message lives in
 * {@link MessageDispatchService}, shared with the REST voice-upload path.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final MessageDispatchService messageDispatchService;
    private final PresenceService presenceService;
    private final CurrentUserProvider currentUserProvider;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());
        conversationService.assertActiveParticipant(request.conversationId(), sender.getId());

        presenceService.recordActivity(principal.getName());

        messageDispatchService.dispatchTextMessage(request.conversationId(), sender.getId(), request.content());
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
