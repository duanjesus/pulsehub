package com.pulsehub.dto.response;

import com.pulsehub.entity.enums.CallSignalType;

/**
 * Sent privately to a single user's {@code /user/queue/calls} destination.
 * Carries the sender's display name/avatar so the callee can render an
 * incoming-call prompt without an extra lookup.
 */
public record CallSignalEvent(
        Long conversationId,
        String callId,
        CallSignalType type,
        String payload,
        Long senderId,
        String senderName,
        String senderAvatarUrl
) {
}
