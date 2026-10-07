package com.pulsehub.controller.ws;

import com.pulsehub.dto.request.CallSignalRequest;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.CallSignalType;
import com.pulsehub.security.CurrentUserProvider;
import com.pulsehub.service.CallSignalingService;
import com.pulsehub.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.EnumSet;
import java.util.Set;

/**
 * WebRTC signaling for 1:1 calls, over the same authenticated STOMP session as
 * chat. Only the handshake (ring, SDP offer/answer, ICE candidates, hang up)
 * passes through here — audio and video flow directly between the two
 * browsers and never touch this server.
 */
@Controller
@RequiredArgsConstructor
public class CallWebSocketController {

    /** Deliberate user actions, plus the in-call keepalive; the burst of ICE candidates is not activity. */
    private static final Set<CallSignalType> COUNTS_AS_ACTIVITY =
            EnumSet.of(CallSignalType.OFFER, CallSignalType.ANSWER, CallSignalType.KEEPALIVE);

    private final CallSignalingService callSignalingService;
    private final PresenceService presenceService;
    private final CurrentUserProvider currentUserProvider;

    @MessageMapping("/call.signal")
    public void signal(@Payload CallSignalRequest request, Principal principal) {
        User sender = currentUserProvider.getUserByEmail(principal.getName());

        callSignalingService.relay(sender.getId(), request);

        if (COUNTS_AS_ACTIVITY.contains(request.type())) {
            presenceService.recordActivity(principal.getName());
        }
    }

}
