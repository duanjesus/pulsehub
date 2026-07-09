package com.pulsehub.controller;

import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.ConversationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> listConversations() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(conversationService.listConversationsForUser(userId));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Long userId = currentUserProvider.getCurrentUserId();
        Page<MessageResponse> messages = conversationService.getMessages(conversationId, userId, PageRequest.of(page, size));
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long conversationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.markAsRead(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

}
