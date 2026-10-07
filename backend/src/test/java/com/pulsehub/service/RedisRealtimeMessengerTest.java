package com.pulsehub.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pulsehub.dto.response.PresenceEvent;
import com.pulsehub.dto.response.ReadReceiptEvent;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.service.impl.RedisRealtimeMessenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRealtimeMessengerTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SimpMessagingTemplate localBroker;

    private ObjectMapper objectMapper;
    private RedisRealtimeMessenger messenger;

    @BeforeEach
    void setUp() {
        // Same settings as application.yml's spring.jackson block.
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        messenger = new RedisRealtimeMessenger(redisTemplate, localBroker, objectMapper);
    }

    private String publishedFrame() {
        ArgumentCaptor<String> frame = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(RedisRealtimeMessenger.CHANNEL), frame.capture());
        return frame.getValue();
    }

    private DefaultMessage redisMessage(String body) {
        return new DefaultMessage(RedisRealtimeMessenger.CHANNEL.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sendToUser_publishesToRedisInsteadOfDeliveringDirectly() throws Exception {
        messenger.sendToUser("grace@pulsehub.dev", "/queue/read-receipts",
                new ReadReceiptEvent(10L, 1L, LocalDateTime.of(2026, 10, 7, 9, 30)));

        JsonNode frame = objectMapper.readTree(publishedFrame());
        assertThat(frame.get("user").asText()).isEqualTo("grace@pulsehub.dev");
        assertThat(frame.get("destination").asText()).isEqualTo("/queue/read-receipts");
        assertThat(frame.get("payload").get("conversationId").asLong()).isEqualTo(10L);
        assertThat(frame.get("payload").get("readAt").asText()).isEqualTo("2026-10-07T09:30:00");
        verifyNoInteractions(localBroker);
    }

    @Test
    void broadcast_publishesAFrameWithNoUser() throws Exception {
        messenger.broadcast("/topic/presence", new PresenceEvent(7L, UserStatus.AWAY));

        JsonNode frame = objectMapper.readTree(publishedFrame());
        assertThat(frame.has("user")).isFalse();
        assertThat(frame.get("destination").asText()).isEqualTo("/topic/presence");
        assertThat(frame.get("payload").get("status").asText()).isEqualTo("AWAY");
        verifyNoInteractions(localBroker);
    }

    @Test
    void onMessage_handsAUserFrameToTheLocalBroker() {
        messenger.sendToUser("grace@pulsehub.dev", "/queue/typing", new PresenceEvent(7L, UserStatus.ONLINE));

        messenger.onMessage(redisMessage(publishedFrame()), null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(localBroker).convertAndSendToUser(eq("grace@pulsehub.dev"), eq("/queue/typing"), payload.capture());
        assertThat(((JsonNode) payload.getValue()).get("userId").asLong()).isEqualTo(7L);
    }

    @Test
    void onMessage_handsABroadcastFrameToTheLocalBroker() {
        messenger.broadcast("/topic/presence", new PresenceEvent(7L, UserStatus.OFFLINE));

        messenger.onMessage(redisMessage(publishedFrame()), null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(localBroker).convertAndSend(eq("/topic/presence"), payload.capture());
        assertThat(((JsonNode) payload.getValue()).get("status").asText()).isEqualTo("OFFLINE");
        verify(localBroker, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void onMessage_dropsAnUnreadableFrameWithoutThrowing() {
        messenger.onMessage(redisMessage("not json"), null);

        verifyNoInteractions(localBroker);
    }

    @Test
    void sendToUser_fallsBackToThisInstanceWhenRedisIsDown() {
        doThrow(new RedisConnectionFailureException("connection refused"))
                .when(redisTemplate).convertAndSend(anyString(), anyString());

        messenger.sendToUser("grace@pulsehub.dev", "/queue/typing", new PresenceEvent(7L, UserStatus.ONLINE));

        verify(localBroker).convertAndSendToUser(eq("grace@pulsehub.dev"), eq("/queue/typing"), any(JsonNode.class));
    }

}
