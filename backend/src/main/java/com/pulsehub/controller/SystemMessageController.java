package com.pulsehub.controller;

import com.pulsehub.dto.request.SystemMessageRequest;
import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.service.SystemMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Producer-only endpoint (guarded by SystemApiKeyAuthFilter, not JWT) for
 * trusted external services to deliver a message from the system bot user
 * into a direct conversation with a PulseHub user. Currently used by
 * PulseQueue's PulseHubNotificationChannel.
 */
@RestController
@RequestMapping("/api/v1/system-messages")
@RequiredArgsConstructor
@Tag(name = "System Messages")
public class SystemMessageController {

    private final SystemMessageService systemMessageService;

    @PostMapping
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody SystemMessageRequest request) {
        MessageResponse response = systemMessageService.sendToUser(request.targetUserId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
