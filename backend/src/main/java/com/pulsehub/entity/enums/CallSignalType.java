package com.pulsehub.entity.enums;

public enum CallSignalType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    REJECT,
    HANGUP,
    BUSY,
    /** Never relayed: sent periodically during a call so the caller's presence doesn't decay to AWAY. */
    KEEPALIVE,
    /** Server-originated only: the callee has no open session to ring. */
    UNAVAILABLE
}
