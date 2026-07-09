package com.pulsehub.service;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;

import java.util.List;

public interface MessageService {

    Message saveMessage(Long conversationId, Long senderId, String content);

    /** Pure builder — {@code readBy} is supplied by the caller so batch lookups can happen once per page. */
    MessageResponse toResponse(Message message, List<Long> readBy);
}
