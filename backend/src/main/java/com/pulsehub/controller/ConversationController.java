package com.pulsehub.controller;

import com.pulsehub.dto.request.AddMemberRequest;
import com.pulsehub.dto.request.CreateDirectConversationRequest;
import com.pulsehub.dto.request.CreateGroupRequest;
import com.pulsehub.dto.response.ConversationResponse;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.dto.response.ParticipantResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.AudioStorageService;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageDispatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageDispatchService messageDispatchService;
    private final AudioStorageService audioStorageService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> listConversations() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(conversationService.listConversationsForUser(userId));
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> createDirectConversation(@Valid @RequestBody CreateDirectConversationRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Conversation conversation = conversationService.getOrCreateDirectConversation(userId, request.otherUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.getConversation(conversation.getId(), userId));
    }

    @PostMapping("/group")
    public ResponseEntity<ConversationResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Conversation conversation = conversationService.createGroupConversation(userId, request.name(), request.memberIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.getConversation(conversation.getId(), userId));
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

    @PostMapping(value = "/{conversationId}/messages/voice", consumes = "multipart/form-data")
    public ResponseEntity<MessageResponse> sendVoiceMessage(
            @PathVariable Long conversationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("durationSeconds") int durationSeconds) {

        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.assertActiveParticipant(conversationId, userId);

        String attachmentUrl = audioStorageService.store(userId, file);
        MessageResponse response = messageDispatchService.dispatchVoiceMessage(conversationId, userId, attachmentUrl, durationSeconds);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long conversationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.markAsRead(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{conversationId}/participants")
    public ResponseEntity<List<ParticipantResponse>> getParticipants(@PathVariable Long conversationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(conversationService.getParticipants(conversationId, userId));
    }

    @PostMapping("/{conversationId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long conversationId, @Valid @RequestBody AddMemberRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.addMember(conversationId, userId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{conversationId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long conversationId, @PathVariable Long memberId) {
        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.removeMember(conversationId, userId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long conversationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        conversationService.leaveConversation(conversationId, userId);
        return ResponseEntity.noContent().build();
    }

}
