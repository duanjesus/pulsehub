package com.pulsehub.service;

import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.impl.PresenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PresenceServiceImpl presenceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(presenceService, "awayAfterMinutes", 5L);
    }

    @Test
    void recordActivity_flipsAwayUserBackToOnlineAndBroadcasts() {
        User user = User.builder().id(1L).email("ada@pulsehub.dev").status(UserStatus.AWAY).build();
        when(userRepository.findByEmail("ada@pulsehub.dev")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        presenceService.recordActivity("ada@pulsehub.dev");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ONLINE);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), any(Object.class));
    }

    @Test
    void recordActivity_doesNotBroadcastWhenAlreadyOnline() {
        User user = User.builder().id(1L).email("ada@pulsehub.dev").status(UserStatus.ONLINE).build();
        when(userRepository.findByEmail("ada@pulsehub.dev")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        presenceService.recordActivity("ada@pulsehub.dev");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void reapInactiveUsers_marksStaleOnlineUsersAsAway() {
        User staleUser = User.builder().id(2L).email("stale@pulsehub.dev").status(UserStatus.ONLINE)
                .lastActivityAt(LocalDateTime.now().minusMinutes(10)).build();

        when(userRepository.findByStatusInAndLastActivityAtBefore(eq(List.of(UserStatus.ONLINE)), any()))
                .thenReturn(List.of(staleUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        presenceService.reapInactiveUsers();

        assertThat(staleUser.getStatus()).isEqualTo(UserStatus.AWAY);
        verify(messagingTemplate).convertAndSend(eq("/topic/presence"), any(Object.class));
    }

}
