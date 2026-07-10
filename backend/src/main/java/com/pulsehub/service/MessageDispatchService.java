package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;

/**
 * Single place that persists a message, broadcasts it to every active
 * participant over STOMP, and fans out notifications — shared by the
 * {@code /app/chat.send} STOMP path (text) and the REST voice-upload path,
 * so the two never drift out of sync.
 */
public interface MessageDispatchService {

    MessageResponse dispatchTextMessage(Long conversationId, Long senderId, String content);

    MessageResponse dispatchVoiceMessage(Long conversationId, Long senderId, String attachmentUrl, int durationSeconds);
}
