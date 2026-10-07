package com.pulsehub.service;

import com.pulsehub.dto.request.CallSignalRequest;
import com.pulsehub.dto.response.IceServerResponse;

import java.util.List;

public interface CallSignalingService {

    /** Relays one WebRTC signaling frame from {@code senderId} to the other participant of a direct conversation. */
    void relay(Long senderId, CallSignalRequest request);

    List<IceServerResponse> getIceServers();
}
