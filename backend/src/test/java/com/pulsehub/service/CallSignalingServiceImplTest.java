package com.pulsehub.service;

import com.pulsehub.config.CallProperties;
import com.pulsehub.dto.request.CallSignalRequest;
import com.pulsehub.dto.response.CallSignalEvent;
import com.pulsehub.dto.response.IceServerResponse;
import com.pulsehub.entity.Conversation;
import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.CallSignalType;
import com.pulsehub.entity.enums.ConversationType;
import com.pulsehub.entity.enums.UserStatus;
import com.pulsehub.exception.BusinessException;
import com.pulsehub.repository.ConversationRepository;
import com.pulsehub.service.impl.CallSignalingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallSignalingServiceImplTest {

    private static final Long CONVERSATION_ID = 10L;
    private static final String CALLS_QUEUE = "/queue/calls";

    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private CallSignalingServiceImpl callSignalingService;

    private User ada;
    private User grace;

    @BeforeEach
    void setUp() {
        CallProperties properties = new CallProperties(List.of(
                new CallProperties.IceServer(List.of("stun:stun.example.org:3478"), null, null),
                new CallProperties.IceServer(List.of("turn:turn.example.org:3478"), "pulsehub", "secret")));
        callSignalingService = new CallSignalingServiceImpl(
                conversationService, conversationRepository, messagingTemplate, properties);

        ada = User.builder().id(1L).name("Ada").email("ada@pulsehub.dev").avatarUrl("/uploads/ada.png")
                .status(UserStatus.ONLINE).build();
        grace = User.builder().id(2L).name("Grace").email("grace@pulsehub.dev").status(UserStatus.ONLINE).build();
    }

    private void givenDirectConversation() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(
                Conversation.builder().id(CONVERSATION_ID).type(ConversationType.DIRECT).build()));
        when(conversationService.getActiveParticipants(CONVERSATION_ID)).thenReturn(List.of(ada, grace));
    }

    private CallSignalRequest request(CallSignalType type, String payload) {
        return new CallSignalRequest(CONVERSATION_ID, "call-1", type, payload);
    }

    @Test
    void relay_forwardsAnOfferToTheOtherParticipantOnly() {
        givenDirectConversation();

        callSignalingService.relay(1L, request(CallSignalType.OFFER, "{\"sdp\":\"v=0\"}"));

        ArgumentCaptor<CallSignalEvent> event = ArgumentCaptor.forClass(CallSignalEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("grace@pulsehub.dev"), eq(CALLS_QUEUE), event.capture());
        verifyNoMoreInteractions(messagingTemplate);

        assertThat(event.getValue()).isEqualTo(new CallSignalEvent(
                CONVERSATION_ID, "call-1", CallSignalType.OFFER, "{\"sdp\":\"v=0\"}", 1L, "Ada", "/uploads/ada.png"));
    }

    @Test
    void relay_tellsTheCallerWhenTheCalleeIsOfflineInsteadOfRingingNobody() {
        grace.setStatus(UserStatus.OFFLINE);
        givenDirectConversation();

        callSignalingService.relay(1L, request(CallSignalType.OFFER, "{\"sdp\":\"v=0\"}"));

        ArgumentCaptor<CallSignalEvent> event = ArgumentCaptor.forClass(CallSignalEvent.class);
        verify(messagingTemplate).convertAndSendToUser(eq("ada@pulsehub.dev"), eq(CALLS_QUEUE), event.capture());
        verifyNoMoreInteractions(messagingTemplate);

        assertThat(event.getValue().type()).isEqualTo(CallSignalType.UNAVAILABLE);
        assertThat(event.getValue().senderId()).isEqualTo(2L);
        assertThat(event.getValue().payload()).isNull();
    }

    @Test
    void relay_ringsAnAwayCallee() {
        grace.setStatus(UserStatus.AWAY);
        givenDirectConversation();

        callSignalingService.relay(1L, request(CallSignalType.OFFER, "{\"sdp\":\"v=0\"}"));

        verify(messagingTemplate).convertAndSendToUser(eq("grace@pulsehub.dev"), eq(CALLS_QUEUE), any(CallSignalEvent.class));
    }

    @Test
    void relay_echoesAnAnswerToTheAnsweringUsersOtherSessions() {
        givenDirectConversation();

        callSignalingService.relay(2L, request(CallSignalType.ANSWER, "{\"sdp\":\"v=0\"}"));

        verify(messagingTemplate).convertAndSendToUser(eq("ada@pulsehub.dev"), eq(CALLS_QUEUE), any(CallSignalEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq("grace@pulsehub.dev"), eq(CALLS_QUEUE), any(CallSignalEvent.class));
    }

    @Test
    void relay_forwardsAHangupWithoutAPayloadEvenIfThePeerWentOffline() {
        grace.setStatus(UserStatus.OFFLINE);
        givenDirectConversation();

        callSignalingService.relay(1L, request(CallSignalType.HANGUP, null));

        verify(messagingTemplate).convertAndSendToUser(eq("grace@pulsehub.dev"), eq(CALLS_QUEUE), any(CallSignalEvent.class));
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void relay_neverForwardsAKeepalive() {
        callSignalingService.relay(1L, request(CallSignalType.KEEPALIVE, null));

        verify(conversationService).assertActiveParticipant(CONVERSATION_ID, 1L);
        verifyNoInteractions(messagingTemplate, conversationRepository);
    }

    @Test
    void relay_rejectsGroupConversations() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(
                Conversation.builder().id(CONVERSATION_ID).type(ConversationType.GROUP).name("Team").build()));

        assertThatThrownBy(() -> callSignalingService.relay(1L, request(CallSignalType.OFFER, "{\"sdp\":\"v=0\"}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direct conversations");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void relay_rejectsASenderWhoIsNotAnActiveParticipant() {
        doThrow(new AccessDeniedException("You are not a participant of this conversation"))
                .when(conversationService).assertActiveParticipant(CONVERSATION_ID, 7L);

        assertThatThrownBy(() -> callSignalingService.relay(7L, request(CallSignalType.OFFER, "{\"sdp\":\"v=0\"}")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(messagingTemplate, conversationRepository);
    }

    @Test
    void relay_rejectsAClientSentUnavailable() {
        assertThatThrownBy(() -> callSignalingService.relay(1L, request(CallSignalType.UNAVAILABLE, null)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(messagingTemplate, conversationService);
    }

    @Test
    void relay_rejectsAnOfferWithoutAPayload() {
        assertThatThrownBy(() -> callSignalingService.relay(1L, request(CallSignalType.OFFER, " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payload");

        verifyNoInteractions(messagingTemplate, conversationService);
    }

    @Test
    void relay_rejectsAMissingCallId() {
        CallSignalRequest request = new CallSignalRequest(CONVERSATION_ID, null, CallSignalType.HANGUP, null);

        assertThatThrownBy(() -> callSignalingService.relay(1L, request))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(messagingTemplate, conversationService);
    }

    @Test
    void getIceServers_exposesTheConfiguredServers() {
        assertThat(callSignalingService.getIceServers()).containsExactly(
                new IceServerResponse(List.of("stun:stun.example.org:3478"), null, null),
                new IceServerResponse(List.of("turn:turn.example.org:3478"), "pulsehub", "secret"));
    }

}
