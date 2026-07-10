package com.pulsehub.service;

import com.pulsehub.dto.response.NotificationResponse;
import com.pulsehub.entity.Notification;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.NotificationType;
import com.pulsehub.mapper.NotificationMapper;
import com.pulsehub.repository.NotificationRepository;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PushSubscriptionService pushSubscriptionService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void notifyNewMessage_persistsAndPushesToRecipientQueue() {
        User recipient = User.builder().id(2L).email("grace@pulsehub.dev").build();
        Notification saved = Notification.builder().id(10L).userId(2L).type(NotificationType.NEW_MESSAGE)
                .title("New message from Ada Lovelace").body("hey there").build();
        NotificationResponse response = new NotificationResponse(10L, NotificationType.NEW_MESSAGE, "t", "b", 1L, null, null);

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(notificationMapper.toResponse(saved)).thenReturn(response);

        notificationService.notifyNewMessage(2L, "Ada Lovelace", "hey there", 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New message from Ada Lovelace");
        assertThat(captor.getValue().getBody()).isEqualTo("hey there");

        verify(messagingTemplate).convertAndSendToUser(eq("grace@pulsehub.dev"), eq("/queue/notifications"), eq(response));
        verify(pushSubscriptionService).sendPush(2L, "New message from Ada Lovelace", "hey there", 1L);
    }

    @Test
    void notifyNewMessage_truncatesLongMessageBodies() {
        String longContent = "x".repeat(300);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        notificationService.notifyNewMessage(2L, "Ada", longContent, 1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).hasSize(201).endsWith("…");
        verifyNoInteractions(messagingTemplate);
    }

}
