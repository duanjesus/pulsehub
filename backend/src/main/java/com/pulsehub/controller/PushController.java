package com.pulsehub.controller;

import com.pulsehub.dto.request.PushSubscribeRequest;
import com.pulsehub.dto.request.PushUnsubscribeRequest;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@Tag(name = "Push notifications")
public class PushController {

    private final PushSubscriptionService pushSubscriptionService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<String> getVapidPublicKey() {
        return ResponseEntity.ok(pushSubscriptionService.getVapidPublicKey());
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        pushSubscriptionService.subscribe(userId, request.endpoint(), request.p256dh(), request.auth());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
        pushSubscriptionService.unsubscribe(request.endpoint());
        return ResponseEntity.noContent().build();
    }

}
