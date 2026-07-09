package com.pulsehub.controller;

import com.pulsehub.dto.response.NotificationResponse;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(notificationService.listForUser(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount() {
        Long userId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(notificationService.countUnread(userId));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        Long userId = currentUserProvider.getCurrentUserId();
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = currentUserProvider.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

}
