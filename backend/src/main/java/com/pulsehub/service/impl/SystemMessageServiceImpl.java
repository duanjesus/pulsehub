package com.pulsehub.service.impl;

import com.pulsehub.dto.response.MessageResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.UserRepository;
import com.pulsehub.service.ConversationService;
import com.pulsehub.service.MessageDispatchService;
import com.pulsehub.service.SystemMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemMessageServiceImpl implements SystemMessageService {

    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MessageDispatchService messageDispatchService;

    @Override
    public MessageResponse sendToUser(Long targetUserId, String content) {
        User systemUser = userRepository.findByEmail(SystemUserInitializer.SYSTEM_BOT_EMAIL)
                .orElseThrow(() -> new ResourceNotFoundException("System bot user not found"));

        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("User not found with id: " + targetUserId);
        }

        Conversation conversation = conversationService.getOrCreateDirectConversation(systemUser.getId(), targetUserId);
        return messageDispatchService.dispatchTextMessage(conversation.getId(), systemUser.getId(), content);
    }
}
