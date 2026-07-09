package com.pulsehub.service.impl;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Message;
import com.pulsehub.mapper.MessageMapper;
import com.pulsehub.repository.MessageRepository;
import com.pulsehub.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;

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
    public MessageResponse toResponse(Message message) {
        return messageMapper.toResponse(message);
    }

}
