package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.UserStatus;

/**
 * Broadcast on {@code /topic/presence} whenever a user's status changes.
 * Presence is public (not per-conversation), matching a Slack-style workspace
 * where everyone can see everyone else's status.
 */
public record PresenceEvent(
        Long userId,
        UserStatus status
) {
}
