package com.pulsehub.service.impl;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;
import com.pulsehub.repository.MessageRepository;
import com.pulsehub.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public Message saveMessage(Long conversationId, Long senderId, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .build();
        return messageRepository.save(message);
    }

    @Override
    public MessageResponse toResponse(Message message, List<Long> readBy) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getContent(),
                message.getSentAt(),
                readBy);
    }

}
