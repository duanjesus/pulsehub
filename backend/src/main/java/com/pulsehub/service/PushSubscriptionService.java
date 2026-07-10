package com.pulsehub.service;

public interface PushSubscriptionService {

    String getVapidPublicKey();

    void subscribe(Long userId, String endpoint, String p256dh, String auth);

    void unsubscribe(String endpoint);

    /** Best-effort: sends a Web Push notification to every subscription the user has, pruning stale ones. */
    void sendPush(Long userId, String title, String body, Long relatedConversationId);
}
