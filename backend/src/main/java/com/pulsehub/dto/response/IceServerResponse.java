package com.pulsehub.dto.response;

import java.util.List;

/** Same shape as the browser's {@code RTCIceServer}, so the client passes it straight through. */
public record IceServerResponse(
        List<String> urls,
        String username,
        String credential
) {
}
