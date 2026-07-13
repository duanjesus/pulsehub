package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;

/**
 * Delivers a message from the system bot user into a direct conversation
 * with the given target user — used by trusted external services (e.g.
 * PulseQueue) via /api/v1/system-messages, not by any human-facing flow.
 */
public interface SystemMessageService {

    MessageResponse sendToUser(Long targetUserId, String content);
}
