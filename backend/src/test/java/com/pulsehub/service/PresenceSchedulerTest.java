package com.pulsehub.service;

import com.pulsehub.service.impl.PresenceScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceSchedulerTest {

    @Mock
    private PresenceService presenceService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private PresenceScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        scheduler = new PresenceScheduler(presenceService, redisTemplate, "instance-a");
    }

    @Test
    void reapInactiveUsers_runsOnTheInstanceThatTakesTheLock() {
        when(valueOperations.setIfAbsent(anyString(), eq("instance-a"), any(Duration.class))).thenReturn(true);

        scheduler.reapInactiveUsers();

        verify(presenceService).reapInactiveUsers();
    }

    @Test
    void reapInactiveUsers_isSkippedWhenAnotherInstanceHoldsTheLock() {
        when(valueOperations.setIfAbsent(anyString(), eq("instance-a"), any(Duration.class))).thenReturn(false);

        scheduler.reapInactiveUsers();

        verifyNoInteractions(presenceService);
    }

    @Test
    void reapInactiveUsers_stillRunsWhenRedisIsDown() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        scheduler.reapInactiveUsers();

        verify(presenceService).reapInactiveUsers();
    }

}
