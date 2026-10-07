package com.pulsehub.service.impl;

import com.pulsehub.entity.User;
import com.pulsehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ensures the system bot account (used to deliver cross-service alerts, e.g.
 * from PulseQueue via /api/v1/system-messages) exists before any request can
 * reach it. Idempotent by email — runs on every boot but only inserts once.
 * The bot's password is a random value nobody knows, encoded like any other
 * user's, since the users.password column is NOT NULL and login is never
 * attempted for this account.
 */
@Component
@RequiredArgsConstructor
public class SystemUserInitializer implements ApplicationRunner {

    public static final String SYSTEM_BOT_EMAIL = "system@pulsehub.internal";
    private static final String SYSTEM_BOT_NAME = "PulseQueue Alerts";

    private static final Logger log = LoggerFactory.getLogger(SystemUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(SYSTEM_BOT_EMAIL)) {
            return;
        }
        try {
            userRepository.save(User.builder()
                    .name(SYSTEM_BOT_NAME)
                    .email(SYSTEM_BOT_EMAIL)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .active(true)
                    .build());
            log.info("Created system bot user ({})", SYSTEM_BOT_EMAIL);
        } catch (DataIntegrityViolationException e) {
            // Another instance booting at the same moment inserted it between our check and our save.
            log.info("System bot user ({}) was created by another instance", SYSTEM_BOT_EMAIL);
        }
    }
}
