package com.pulsehub.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsehub.entity.PushSubscription;
import com.pulsehub.repository.PushSubscriptionRepository;
import com.pulsehub.service.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionServiceImpl.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    @Value("${pulsehub.push.vapid.public-key}")
    private String vapidPublicKey;

    @Override
    public String getVapidPublicKey() {
        return vapidPublicKey;
    }

    @Override
    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(() -> PushSubscription.builder().endpoint(endpoint).build());

        subscription.setUserId(userId);
        subscription.setP256dhKey(p256dh);
        subscription.setAuthKey(auth);
        pushSubscriptionRepository.save(subscription);
    }

    @Override
    @Transactional
    public void unsubscribe(String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
    }

    @Override
    @Transactional
    public void sendPush(Long userId, String title, String body, Long relatedConversationId) {
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "relatedConversationId", relatedConversationId));
        } catch (Exception e) {
            log.error("Could not serialize push payload", e);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            try {
                Subscription.Keys keys = new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey());
                Subscription webPushSubscription = new Subscription(subscription.getEndpoint(), keys);
                Notification notification = new Notification(webPushSubscription, payload);

                var response = pushService.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == HttpStatus.GONE.value() || statusCode == HttpStatus.NOT_FOUND.value()) {
                    pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
                }
            } catch (Exception e) {
                log.warn("Failed to send push notification to subscription {}: {}", subscription.getId(), e.getMessage());
            }
        }
    }

}
