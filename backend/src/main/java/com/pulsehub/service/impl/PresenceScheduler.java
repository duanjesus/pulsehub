package com.pulsehub.service.impl;

import com.pulsehub.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresenceScheduler {

    private final PresenceService presenceService;

    @Scheduled(fixedRate = 60_000)
    public void reapInactiveUsers() {
        presenceService.reapInactiveUsers();
    }

}
