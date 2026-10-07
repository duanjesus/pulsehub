package com.pulsehub.service.impl;

import com.pulsehub.config.CallProperties;
import com.pulsehub.dto.request.CallSignalRequest;
import com.pulsehub.dto.response.CallSignalEvent;
import com.pulsehub.dto.response.IceServerResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.CallSignalType;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.exception.BusinessException;
import com.pulsehub.exception.ResourceNotFoundException;
import com.pulsehub.repository.ConversationRepository;
import com.pulsehub.service.CallSignalingService;
import com.pulsehub.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A stateless relay: the server never tracks whether a call is ringing or
 * connected, it only checks that the sender may signal in this conversation
 * and forwards the frame. Call state lives in the two browsers, which is what
 * lets this work unchanged across several backend instances.
 */
@Service
@RequiredArgsConstructor
public class CallSignalingServiceImpl implements CallSignalingService {

    private static final String CALLS_QUEUE = "/queue/calls";
    private static final int MAX_CALL_ID_LENGTH = 64;

    private static final Set<CallSignalType> TYPES_WITH_PAYLOAD =
            EnumSet.of(CallSignalType.OFFER, CallSignalType.ANSWER, CallSignalType.ICE_CANDIDATE);

    /** Echoed to the sender's own sessions so their other open tabs stop ringing. */
    private static final Set<CallSignalType> TYPES_ECHOED_TO_SENDER =
            EnumSet.of(CallSignalType.ANSWER, CallSignalType.REJECT);

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CallProperties callProperties;

    @Override
    public void relay(Long senderId, CallSignalRequest request) {
        validate(request);
        conversationService.assertActiveParticipant(request.conversationId(), senderId);

        if (request.type() == CallSignalType.KEEPALIVE) {
            return;
        }

        Conversation conversation = conversationRepository.findById(request.conversationId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + request.conversationId()));
        if (!conversation.isDirect()) {
            throw new BusinessException("Calls are only available in direct conversations");
        }

        List<User> participants = conversationService.getActiveParticipants(request.conversationId());
        User sender = participants.stream()
                .filter(user -> user.getId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + senderId));
        User peer = participants.stream()
                .filter(user -> !user.getId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("The other participant is no longer in this conversation"));

        if (request.type() == CallSignalType.OFFER && peer.getStatus() == UserStatus.OFFLINE) {
            messagingTemplate.convertAndSendToUser(sender.getEmail(), CALLS_QUEUE,
                    toEvent(request, CallSignalType.UNAVAILABLE, null, peer));
            return;
        }

        CallSignalEvent event = toEvent(request, request.type(), request.payload(), sender);
        messagingTemplate.convertAndSendToUser(peer.getEmail(), CALLS_QUEUE, event);

        if (TYPES_ECHOED_TO_SENDER.contains(request.type())) {
            messagingTemplate.convertAndSendToUser(sender.getEmail(), CALLS_QUEUE, event);
        }
    }

    @Override
    public List<IceServerResponse> getIceServers() {
        return callProperties.iceServers().stream()
                .map(server -> new IceServerResponse(server.urls(), server.username(), server.credential()))
                .toList();
    }

    private void validate(CallSignalRequest request) {
        if (request.conversationId() == null || request.type() == null) {
            throw new BusinessException("conversationId and type are required");
        }
        if (request.callId() == null || request.callId().isBlank() || request.callId().length() > MAX_CALL_ID_LENGTH) {
            throw new BusinessException("callId is required and must be at most " + MAX_CALL_ID_LENGTH + " characters");
        }
        if (request.type() == CallSignalType.UNAVAILABLE) {
            throw new BusinessException("UNAVAILABLE can only be sent by the server");
        }
        if (TYPES_WITH_PAYLOAD.contains(request.type()) && (request.payload() == null || request.payload().isBlank())) {
            throw new BusinessException(request.type() + " requires a payload");
        }
    }

    private CallSignalEvent toEvent(CallSignalRequest request, CallSignalType type, String payload, User from) {
        return new CallSignalEvent(request.conversationId(), request.callId(), type, payload,
                from.getId(), from.getName(), from.getAvatarUrl());
    }

}
