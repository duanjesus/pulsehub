package com.pulsehub.service;

/**
 * The only way application code pushes anything to a browser. The recipient's
 * WebSocket may be held by any backend instance, so callers never talk to the
 * local STOMP broker directly.
 */
public interface RealtimeMessenger {

    /** Delivers to every open session of one user, e.g. {@code /queue/messages}. */
    void sendToUser(String email, String destination, Object payload);

    /** Delivers to every subscriber of a public topic, e.g. {@code /topic/presence}. */
    void broadcast(String destination, Object payload);
}
