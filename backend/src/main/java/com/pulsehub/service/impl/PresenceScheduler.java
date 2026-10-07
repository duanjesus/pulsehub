package com.pulsehub.service.impl;

import com.pulsehub.service.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Every instance runs this timer, but only the one that wins a short-lived
 * Redis lock does the work — otherwise N instances would each flip the same
 * idle users to AWAY and broadcast the change N times.
 */
@Component
public class PresenceScheduler {

    static final String LOCK_KEY = "pulsehub:lock:presence-reaper";
    /** Shorter than the 60s tick, so a lock taken on one tick never blocks the next. */
    static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private static final Logger log = LoggerFactory.getLogger(PresenceScheduler.class);

    private final PresenceService presenceService;
    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    public PresenceScheduler(PresenceService presenceService, StringRedisTemplate redisTemplate,
                             @Value("${pulsehub.instance-id}") String instanceId) {
        this.presenceService = presenceService;
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    @Scheduled(fixedRate = 60_000)
    public void reapInactiveUsers() {
        if (acquiredLock()) {
            presenceService.reapInactiveUsers();
        }
    }

    private boolean acquiredLock() {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL));
        } catch (RuntimeException e) {
            // Without Redis there's no way to coordinate; a duplicated run is harmless, a skipped one is not.
            log.warn("Could not take the presence-reaper lock, running anyway: {}", e.getMessage());
            return true;
        }
    }

}
