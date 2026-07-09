package com.pulsehub.service.impl;

import com.pulsehub.dto.response.PresenceEvent;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private static final String PRESENCE_TOPIC = "/topic/presence";

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${pulsehub.presence.away-after-minutes}")
    private long awayAfterMinutes;

    @Override
    @Transactional
    public void markOnline(String email) {
        User user = findUser(email);
        user.setStatus(UserStatus.ONLINE);
        user.setLastActivityAt(LocalDateTime.now());
        userRepository.save(user);
        broadcast(user);
    }

    @Override
    @Transactional
    public void markOffline(String email) {
        User user = findUser(email);
        user.setStatus(UserStatus.OFFLINE);
        user.setLastSeenAt(LocalDateTime.now());
        userRepository.save(user);
        broadcast(user);
    }

    @Override
    @Transactional
    public void recordActivity(String email) {
        User user = findUser(email);
        boolean wasAway = user.getStatus() == UserStatus.AWAY;
        user.setLastActivityAt(LocalDateTime.now());
        if (wasAway) {
            user.setStatus(UserStatus.ONLINE);
        }
        userRepository.save(user);
        if (wasAway) {
            broadcast(user);
        }
    }

    @Override
    @Transactional
    public void reapInactiveUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(awayAfterMinutes);
        List<User> inactive = userRepository.findByStatusInAndLastActivityAtBefore(List.of(UserStatus.ONLINE), threshold);

        for (User user : inactive) {
            user.setStatus(UserStatus.AWAY);
            userRepository.save(user);
            broadcast(user);
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private void broadcast(User user) {
        messagingTemplate.convertAndSend(PRESENCE_TOPIC, new PresenceEvent(user.getId(), user.getStatus()));
    }

}
