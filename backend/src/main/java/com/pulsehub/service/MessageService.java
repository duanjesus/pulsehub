package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;

public interface MessageService {
    Message saveMessage(Long conversationId, Long senderId, String content);

    MessageResponse toResponse(Message message);
}
