package com.pulsehub.service.impl;

import com.pulsehub.dto.response.NotificationResponse;
import com.pulsehub.entity.Notification;
import com.pulsehub.entity.enums.NotificationType;
import com.pulsehub.mapper.NotificationMapper;
import com.pulsehub.repository.NotificationRepository;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.NotificationService;
import com.pulsehub.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int BODY_PREVIEW_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushSubscriptionService pushSubscriptionService;

    @Override
    @Transactional
    public void notifyNewMessage(Long recipientId, String senderName, String messageContent, Long conversationId) {
        Notification notification = Notification.builder()
                .userId(recipientId)
                .type(NotificationType.NEW_MESSAGE)
                .title("New message from " + senderName)
                .body(truncate(messageContent))
                .relatedConversationId(conversationId)
                .build();

        Notification saved = notificationRepository.save(notification);

        userRepository.findById(recipientId).ifPresent(recipient -> messagingTemplate.convertAndSendToUser(
                recipient.getEmail(), "/queue/notifications", notificationMapper.toResponse(saved)));

        pushSubscriptionService.sendPush(recipientId, saved.getTitle(), saved.getBody(), conversationId);
    }

    @Override
    public Page<NotificationResponse> listForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsRead(notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private String truncate(String content) {
        if (content.length() <= BODY_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, BODY_PREVIEW_LENGTH) + "…";
    }

}
