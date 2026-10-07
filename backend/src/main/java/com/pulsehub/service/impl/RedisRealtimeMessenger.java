package com.pulsehub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsehub.service.RealtimeMessenger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Fans every outbound frame across all backend instances through one Redis
 * pub/sub channel. Each instance keeps its own in-memory STOMP broker, which
 * only knows the sessions connected to it — so a frame is published once, every
 * instance receives it, and each hands it to its local broker, which delivers
 * to whichever of those sessions it actually holds (and drops it otherwise).
 *
 * The publishing instance does not deliver directly: it gets its own frame back
 * from Redis like everyone else, so there is exactly one delivery path.
 */
@Service
@RequiredArgsConstructor
public class RedisRealtimeMessenger implements RealtimeMessenger, MessageListener {

    public static final String CHANNEL = "pulsehub:realtime";

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeMessenger.class);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate localBroker;
    private final ObjectMapper objectMapper;

    /** {@code user} is null for a topic broadcast. */
    record Envelope(String user, String destination, JsonNode payload) {
    }

    @Override
    public void sendToUser(String email, String destination, Object payload) {
        publish(new Envelope(email, destination, objectMapper.valueToTree(payload)));
    }

    @Override
    public void broadcast(String destination, Object payload) {
        publish(new Envelope(null, destination, objectMapper.valueToTree(payload)));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliverLocally(objectMapper.readValue(message.getBody(), Envelope.class));
        } catch (Exception e) {
            log.error("Dropped an unreadable realtime frame from Redis", e);
        }
    }

    private void publish(Envelope envelope) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize a realtime frame for " + envelope.destination(), e);
        } catch (RuntimeException e) {
            // Redis is unreachable: users on this instance can still be served, the others miss the frame.
            log.warn("Redis publish failed, delivering to this instance only: {}", e.getMessage());
            deliverLocally(envelope);
        }
    }

    private void deliverLocally(Envelope envelope) {
        if (envelope.user() == null) {
            localBroker.convertAndSend(envelope.destination(), envelope.payload());
        } else {
            localBroker.convertAndSendToUser(envelope.user(), envelope.destination(), envelope.payload());
        }
    }

}
