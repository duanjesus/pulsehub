package com.pulsehub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsehub.entity.PushSubscription;
import com.pulsehub.repository.PushSubscriptionRepository;
import com.pulsehub.service.impl.PushSubscriptionServiceImpl;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImplTest {

    // A syntactically valid (but not secret) P-256 point / 16-byte auth secret,
    // base64url-encoded — the web-push library parses these as real EC key
    // material, so arbitrary short strings like "p256dh-key" throw deep inside it.
    private static final String VALID_P256DH =
            "BIlJvUJgxEtUgdR3wEiYac9Bg9YFoEcFKiIIdwfdahiHjRTUOFlRxXuM-DH_MQ2w5U2DPVeiUQeyYardHP8FWXk";
    private static final String VALID_AUTH = "8zj4YpeTTjP6BYnkRp9BCg";

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock
    private PushService pushService;

    @InjectMocks
    private PushSubscriptionServiceImpl pushSubscriptionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getVapidPublicKey_returnsConfiguredValue() {
        ReflectionTestUtils.setField(pushSubscriptionService, "vapidPublicKey", "test-public-key");

        assertThat(pushSubscriptionService.getVapidPublicKey()).isEqualTo("test-public-key");
    }

    @Test
    void subscribe_createsNewSubscriptionWhenEndpointIsUnseen() {
        ReflectionTestUtils.setField(pushSubscriptionService, "objectMapper", objectMapper);
        when(pushSubscriptionRepository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.empty());

        pushSubscriptionService.subscribe(1L, "https://push.example/abc", "p256dh-key", "auth-key");

        var captor = org.mockito.ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push.example/abc");
    }

    @Test
    void subscribe_updatesExistingSubscriptionForTheSameEndpoint() {
        ReflectionTestUtils.setField(pushSubscriptionService, "objectMapper", objectMapper);
        PushSubscription existing = PushSubscription.builder().id(9L).endpoint("https://push.example/abc").userId(1L).build();
        when(pushSubscriptionRepository.findByEndpoint("https://push.example/abc")).thenReturn(Optional.of(existing));

        pushSubscriptionService.subscribe(1L, "https://push.example/abc", "new-p256dh", "new-auth");

        assertThat(existing.getP256dhKey()).isEqualTo("new-p256dh");
        assertThat(existing.getAuthKey()).isEqualTo("new-auth");
        verify(pushSubscriptionRepository).save(existing);
    }

    @Test
    void unsubscribe_deletesByEndpoint() {
        pushSubscriptionService.unsubscribe("https://push.example/abc");

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example/abc");
    }

    @Test
    void sendPush_doesNothingWhenUserHasNoSubscriptions() throws Exception {
        when(pushSubscriptionRepository.findByUserId(1L)).thenReturn(List.of());

        pushSubscriptionService.sendPush(1L, "title", "body", 5L);

        verifyNoInteractions(pushService);
    }

    @Test
    void sendPush_prunesSubscriptionWhenTheEndpointIsGone() throws Exception {
        ReflectionTestUtils.setField(pushSubscriptionService, "objectMapper", objectMapper);
        PushSubscription subscription = PushSubscription.builder()
                .id(1L).endpoint("https://push.example/stale").p256dhKey(VALID_P256DH).authKey(VALID_AUTH).build();
        when(pushSubscriptionRepository.findByUserId(1L)).thenReturn(List.of(subscription));

        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(410);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any())).thenReturn(response);

        pushSubscriptionService.sendPush(1L, "title", "body", 5L);

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example/stale");
    }

    @Test
    void sendPush_keepsSubscriptionOnSuccess() throws Exception {
        ReflectionTestUtils.setField(pushSubscriptionService, "objectMapper", objectMapper);
        PushSubscription subscription = PushSubscription.builder()
                .id(1L).endpoint("https://push.example/ok").p256dhKey(VALID_P256DH).authKey(VALID_AUTH).build();
        when(pushSubscriptionRepository.findByUserId(1L)).thenReturn(List.of(subscription));

        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(201);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(pushService.send(any())).thenReturn(response);

        pushSubscriptionService.sendPush(1L, "title", "body", 5L);

        verify(pushSubscriptionRepository, never()).deleteByEndpoint(any());
    }

}
