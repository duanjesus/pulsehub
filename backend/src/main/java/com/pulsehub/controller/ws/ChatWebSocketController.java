package com.pulsehub.controller.ws;

import com.pulsehub.dto.request.SendMessageRequest;
import com.pulsehub.dto.request.TypingRequest;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.TypingEvent;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.Message;
import com.pulsehub.entity.User;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageService;
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
 * {@link com.pulsehub.security.WebSocketAuthChannelInterceptor} on CONNECT —
 * messages are always sent to the recipient's {@code /user/queue/*}, never
 * broadcast to a shared conversation topic, since only the two participants
 * should ever see them.
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());
        User recipient = userRepository.findById(request.recipientId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.recipientId()));

        presenceService.recordActivity(principal.getName());

        Conversation conversation = conversationService.getOrCreateConversation(sender.getId(), recipient.getId());
        Message message = messageService.saveMessage(conversation.getId(), sender.getId(), request.content());
        MessageResponse response = messageService.toResponse(message);

        messagingTemplate.convertAndSendToUser(recipient.getEmail(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(sender.getEmail(), "/queue/messages", response);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());
        User recipient = userRepository.findById(request.recipientId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.recipientId()));

        Conversation conversation = conversationService.getOrCreateConversation(sender.getId(), recipient.getId());
        TypingEvent event = new TypingEvent(conversation.getId(), sender.getId(), request.typing());

        messagingTemplate.convertAndSendToUser(recipient.getEmail(), "/queue/typing", event);
    }

}
