<div align="center">

# PulseHub — Backend

### Spring Boot backend for a real-time communication platform: JWT auth, direct/group messaging (text and voice), presence, typing indicators, read receipts, real-time notifications, Web Push and 1:1 video-call signaling over STOMP/WebSocket

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?logo=flyway)](https://flywaydb.org/)

</div>

> This is the backend half of the [PulseHub monorepo](../README.md). See the root README for the full-stack quick start (API + React frontend together).

---

## 🏗️ Architecture

```
                                ┌───────────────────┐
                                │     Controller       │  → REST endpoints + STOMP @MessageMapping
                                └─────────┬───────────┘
                                          │
                                ┌─────────▼───────────┐
                                │       Service          │  → Business rules, participant checks, transactions
                                └─────────┬───────────┘
                                          │
                         ┌────────────────┼────────────────┐
                         │                                 │
               ┌─────────▼──────────┐          ┌───────────▼───────────┐
               │      Mapper           │          │      Repository         │  → Data access (Spring Data JPA)
               │ (Entity → Response DTO) │          └───────────┬───────────┘
               └───────────────────────┘                      │
                                                      ┌────────▼────────┐
                                                      │   PostgreSQL       │
                                                      └───────────────────┘

    REST requests are authenticated by a servlet filter (JwtAuthenticationFilter).
    STOMP CONNECT frames are authenticated by a ChannelInterceptor
    (WebSocketAuthChannelInterceptor) that validates the same JWT and attaches
    a Principal to the session — every later frame on that session (SEND,
    SUBSCRIBE) is then authenticated for free.
```

### Real-time flow

```
Client A                    Server                     Client B
   │                          │                            │
   │  CONNECT (JWT header)    │                            │
   ├─────────────────────────▶│                            │
   │                          │  SessionConnectedEvent      │
   │                          │  → status=ONLINE            │
   │                          │  → /topic/presence broadcast│
   │                          ├───────────────────────────▶│ (everyone sees A online)
   │                          │                            │
   │ SEND /app/chat.send (conversationId)                    │
   ├─────────────────────────▶│  persist Message             │
   │                          │  for EVERY active participant:
   │                          │  convertAndSendToUser(x) ───▶│ /user/queue/messages
   │                          │  (sender included, for multi-tab sync)
   │                          │                            │
   │ SEND /app/chat.typing (conversationId)                  │
   ├─────────────────────────▶│  for every OTHER participant:
   │                          │  convertAndSendToUser(x) ───▶│ /user/queue/typing
   │                          │                            │
   │ POST /conversations/1/read (B reads A's message)       │
   ├─────────────────────────▶│  insert MessageRead rows     │
   │                          │  for every OTHER participant:
   │                          │  convertAndSendToUser(x) ───▶│ /user/queue/read-receipts
   │                          │  (A's sent message's "Read N/M" count ticks up live)
   │                          │                            │
   │  DISCONNECT              │                            │
   ├─────────────────────────▶│  status=OFFLINE, lastSeenAt │
   │                          ├───────────────────────────▶│ /topic/presence
```

This flow is identical for a 1:1 chat and an N-person group — "every active participant" is 1 other person for a DIRECT conversation and N-1 for a GROUP. Messages, typing and read receipts are always sent to specific users' private queues via `SimpMessagingTemplate.convertAndSendToUser`, never broadcast to a shared conversation topic — only current participants ever see them (a removed/left member stops receiving anything immediately). Presence, by contrast, is public: everyone subscribes to `/topic/presence` (Slack-style workspace, not per-conversation visibility). A user idle for `pulsehub.presence.away-after-minutes` (default 5) while still connected is flipped from `ONLINE` to `AWAY` by a scheduled job (`PresenceScheduler`), and back to `ONLINE` the moment any STOMP frame from that session records activity again.

Every new message also creates a persisted `Notification` row for **each** recipient (`NotificationService.notifyNewMessage`, called once per participant other than the sender) and pushes it to `/user/queue/notifications` if they're connected — this is what powers the notification bell and the dashboard's Notifications card; it's independent of message read state (reading a conversation does not automatically mark its notification as read, and vice versa).

### Data model: direct vs. group

A `Conversation` is either `DIRECT` (exactly 2 people) or `GROUP` (named, N people with roles). Both share the same `conversation_participants` roster table (`user_id`, `role` — `OWNER`/`MEMBER`, `joined_at`, `left_at`) instead of the fixed pair of columns V1/V2 used — a `DIRECT` conversation is deduplicated via a `direct_key` (`"<lowerUserId>_<higherUserId>"`, unique per type) rather than by scanning participants. Message read state also moved off `messages` and into its own `message_reads` table (`message_id`, `user_id`, `read_at`) so a message can be read by anywhere from 0 to N−1 people — the API exposes this as `readBy: number[]` on each message, and the frontend renders it as `✓`/`✓✓` for direct chats or `Read N/M` for groups. See `backend/src/main/resources/db/migration/V5__add_group_conversations.sql` for the exact migration, including how existing V1/V2 data was backfilled into the new tables.

Only a `GROUP`'s `OWNER` can add/remove members (`ConversationService.addMember`/`removeMember`); anyone can leave (`leaveConversation`), and if the owner leaves, the longest-tenured remaining member is automatically promoted so the group is never left without one.

### Voice messages and Web Push

A `Message` is either `TEXT` (`content` set) or `VOICE` (`attachmentUrl` + `attachmentDurationSeconds` set, `content` null — enforced by a DB `CHECK` constraint). Both kinds go through the same `MessageDispatchService`, which persists the message, broadcasts it to every active participant, and fans out a `Notification` to everyone but the sender — this one place is shared by the STOMP `/app/chat.send` path (text) and the REST voice-upload path, so they can never drift out of sync. A voice message is uploaded via `POST /api/v1/conversations/{id}/messages/voice` (multipart: `file` + `durationSeconds`), validated and stored by `AudioStorageServiceImpl` the same way avatars are, and served back from `/uploads/voice/**`.

Real Web Push (VAPID) delivers a browser notification even when the tab is closed. `NotificationServiceImpl.notifyNewMessage` calls `PushSubscriptionService.sendPush` after creating the in-app `Notification` — this is best-effort: failures are caught and logged, never allowed to break message sending, and a `410 Gone`/`404 Not Found` response from the push service (the browser's subscription is dead) silently prunes that `PushSubscription` row. The `nl.martijndwars:web-push` library needs the BouncyCastle security provider registered *before* it parses the VAPID keys — this is done in a `static` block in both `WebPushConfig` (which builds the shared `PushService` bean) and `PushSubscriptionServiceImpl`, deliberately redundant, because Spring doesn't guarantee bean/class initialization order and relying on just one of them caused a real `NoSuchProviderException: no such provider: BC` crash at startup during development.

VAPID keys live in `pulsehub.push.vapid.*` (`application.yml`), with dev defaults committed the same way `JWT_SECRET` is — override `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`/`VAPID_SUBJECT` for any real deployment. Generate a fresh pair with Node's built-in `crypto` (`generateKeyPairSync('ec', { namedCurve: 'prime256v1' })`, then export as raw base64url) or the `web-push` npm CLI — no need to add either as a project dependency just to generate keys once.

### Video calls: WebRTC signaling

A 1:1 video call is two browsers exchanging media directly over WebRTC. The server's only job is **signaling**: carrying the SDP offer/answer and the ICE candidates the browsers need to find each other. Audio and video never pass through it.

```
Caller                      Server                      Callee
  │ SEND /app/call.signal     │                            │
  │   OFFER + SDP             │  DIRECT conversation?       │
  ├──────────────────────────▶│  sender is a participant?   │
  │                           │  callee OFFLINE? ─▶ UNAVAILABLE back to caller
  │                           ├───────────────────────────▶│ /user/queue/calls  (rings)
  │   ICE_CANDIDATE × n       ├───────────────────────────▶│ (buffered until accepted)
  │                           │       ANSWER + SDP          │
  │ /user/queue/calls ◀───────┼─────────────────────────────┤ (also echoed to the callee's
  │                           │   ICE_CANDIDATE × n         │  other tabs, so they stop ringing)
  │◀──────────────────────────┼─────────────────────────────┤
  │◀═══════════════ audio + video, peer to peer ═══════════▶│
  │   HANGUP                  ├───────────────────────────▶│
```

Everything is one STOMP destination, `/app/call.signal`, taking `{conversationId, callId, type, payload}`. `CallSignalingServiceImpl` validates the frame, checks the sender is an active participant of a `DIRECT` conversation, and forwards it to the other participant's `/user/queue/calls` with the sender's id, name and avatar attached. `payload` is the JSON-serialized SDP description or ICE candidate and is opaque to the server. `callId` is generated by the caller's browser and identifies one call attempt end to end.

| `type`          | Sent by | Meaning |
|------------------|---------|---------|
| `OFFER`          | caller  | Start ringing; carries the caller's SDP |
| `ANSWER`         | callee  | Accepted; carries the callee's SDP. Echoed to the callee's own sessions |
| `ICE_CANDIDATE`  | either  | One network path the peer can try |
| `REJECT`         | callee  | Declined. Echoed to the callee's own sessions |
| `HANGUP`         | either  | End (or cancel) the call |
| `BUSY`           | callee  | Sent automatically when an `OFFER` arrives mid-call |
| `KEEPALIVE`      | either  | Never relayed; counts as presence activity so a long call doesn't decay to `AWAY` |
| `UNAVAILABLE`    | server  | Reply to an `OFFER` whose callee is `OFFLINE`, instead of ringing nobody |

**The relay is deliberately stateless.** The server never records that a call is ringing or connected, so there is nothing to clean up when a browser disappears and nothing to share between backend instances. The cost is that every timeout lives in the client (30s unanswered on the caller, 45s on the callee, and WebRTC's own `failed` state mid-call). Spring doesn't guarantee the order of frames from one session, so an `ICE_CANDIDATE` can overtake the `OFFER`/`ANSWER` it belongs to; the client buffers candidates per `callId` until the remote description is set.

ICE servers come from `GET /api/v1/calls/ice-servers`, backed by `pulsehub.call.ice-servers` (`CallProperties`), so they can change without rebuilding the frontend. The default is a single public STUN server, which is enough when both peers can reach each other directly. **There is no TURN server**: two peers behind strict (symmetric) NATs will fail to connect until one is added to that list (`urls` + `username` + `credential`). Group calls are out of scope — they need an SFU or a full mesh, not a relay between two peers.

### Package layout

```
backend/src/main/java/com/pulsehub
├── controller
│   └── ws         # STOMP @MessageMapping controllers + WebSocket session listener
├── service        # Business rule interfaces
│   └── impl       # Concrete service implementations
├── repository     # Spring Data JPA interfaces
├── entity         # JPA entities (database mapping)
│   └── enums      # Domain enumerations
├── dto
│   ├── request    # Input objects, with Bean Validation
│   └── response   # Output objects
├── mapper         # Entity → response DTO conversion (MapStruct)
├── config         # WebSocket/STOMP, Security, CORS, Swagger
├── exception      # Custom exceptions + GlobalExceptionHandler
└── security       # JWT filter/service, WebSocket auth interceptor, CurrentUserProvider
```

---

## 🧰 Tech stack

| Category      | Technology                          |
|---------------|--------------------------------------|
| Language      | Java 21                              |
| Framework     | Spring Boot 3.3                      |
| Real-time     | Spring WebSocket + STOMP, SockJS fallback |
| Persistence   | Spring Data JPA + Hibernate           |
| Migrations    | Flyway (`ddl-auto: validate`)          |
| Database      | PostgreSQL 16                         |
| Auth          | Spring Security + JWT (jjwt, HS256)   |
| Push          | Web Push / VAPID (`nl.martijndwars:web-push` + BouncyCastle) |
| Calls         | WebRTC signaling relayed over STOMP (media is peer to peer) |
| Mapping       | MapStruct                             |
| Docs          | springdoc-openapi (Swagger UI)         |
| Tests         | JUnit 5, Mockito, AssertJ              |

---

## 🚀 Running locally

```bash
# Database only
docker compose up -d db   # from repo root

cd backend
mvn spring-boot:run
```

API on `http://localhost:8080`, Swagger UI on `http://localhost:8080/swagger-ui.html`.

## 🔌 API overview

| Method | Path                                   | Description                          |
|--------|-----------------------------------------|----------------------------------------|
| POST   | `/api/v1/auth/register`                 | Create an account, returns a JWT       |
| POST   | `/api/v1/auth/login`                    | Authenticate, returns a JWT            |
| GET    | `/api/v1/users`                         | List every other user with presence    |
| GET    | `/api/v1/users/me`                      | Get the caller's own profile           |
| PUT    | `/api/v1/users/me`                      | Update display name                    |
| PUT    | `/api/v1/users/me/password`             | Change password (requires current password) |
| POST   | `/api/v1/users/me/avatar`               | Upload an avatar image (multipart, ≤5MB) |
| GET    | `/api/v1/conversations`                 | List the caller's conversations (direct + group) |
| POST   | `/api/v1/conversations/direct`          | Get-or-create a 1:1 conversation with `otherUserId` |
| POST   | `/api/v1/conversations/group`           | Create a group (`name` + `memberIds`), caller becomes OWNER |
| GET    | `/api/v1/conversations/{id}/messages`   | Paginated message history              |
| POST   | `/api/v1/conversations/{id}/messages/voice` | Upload a voice message (multipart: `file` + `durationSeconds`) |
| POST   | `/api/v1/conversations/{id}/read`       | Mark messages as read, notifies every other participant |
| GET    | `/api/v1/conversations/{id}/participants` | List active participants with roles  |
| POST   | `/api/v1/conversations/{id}/members`    | Add a member (OWNER only)              |
| DELETE | `/api/v1/conversations/{id}/members/{userId}` | Remove a member (OWNER only)     |
| POST   | `/api/v1/conversations/{id}/leave`      | Leave a conversation (auto-promotes a new OWNER if needed) |
| GET    | `/api/v1/notifications`                 | Paginated notification history         |
| GET    | `/api/v1/notifications/unread-count`    | Unread notification count              |
| POST   | `/api/v1/notifications/{id}/read`       | Mark one notification as read          |
| POST   | `/api/v1/notifications/read-all`        | Mark every notification as read        |
| GET    | `/api/v1/dashboard`                     | Online users, recent chats, unread counts, recent notifications |
| GET    | `/api/v1/push/vapid-public-key`         | The VAPID public key the frontend needs to subscribe |
| POST   | `/api/v1/push/subscribe`                | Save a browser's push subscription (endpoint + keys) |
| POST   | `/api/v1/push/unsubscribe`              | Remove a push subscription by endpoint |
| GET    | `/api/v1/calls/ice-servers`             | STUN/TURN servers the browser should use for a call |

| STOMP endpoint            | Direction | Description                              |
|----------------------------|-----------|--------------------------------------------|
| `/ws` (SockJS)              | —         | Connection endpoint, JWT in the CONNECT header |
| `/app/chat.send`            | client → server | Send a message to `conversationId`  |
| `/app/chat.typing`          | client → server | Notify `conversationId` of typing state |
| `/user/queue/messages`      | server → client | New message for this user (any conversation) |
| `/user/queue/typing`        | server → client | Typing state from a peer in some conversation |
| `/user/queue/read-receipts` | server → client | A conversation you sent messages in was just read by someone |
| `/user/queue/notifications` | server → client | A new notification was created for you |
| `/app/call.signal`          | client → server | One WebRTC signaling frame for a direct conversation |
| `/user/queue/calls`         | server → client | A signaling frame from the other side of a call |
| `/topic/presence`           | server → client | Any user's status changed           |

Uploaded avatars are served back as static files from `/uploads/**` (public, no JWT required — `<img>` tags can't attach an Authorization header) and stored on the `pulsehub-uploads-data` Docker volume so they survive container restarts; see `pulsehub.uploads.dir` in `application.yml` and `AvatarStorageServiceImpl`.

## 🧪 Tests

```bash
mvn test
```

`AuthServiceImplTest`, `ConversationServiceImplTest`, `PresenceServiceImplTest`, `NotificationServiceImplTest`, `UserServiceImplTest`, `MessageDispatchServiceImplTest`, `PushSubscriptionServiceImplTest` and `AudioStorageServiceImplTest` cover the registration/login rules, the online/away presence transitions, notification creation/push, profile updates, the shared text/voice dispatch path, Web Push subscribe/unsubscribe/send (including stale-subscription pruning on a `410`/`404`), and audio upload validation — all with Mockito, no real database needed. `ConversationServiceImplTest` is the largest: direct-conversation dedup via `directKey`, group creation (owner + members), add/remove-member permission checks, leave-with-owner-promotion, and the multi-participant read-receipt broadcast.

`CallSignalingServiceImplTest` covers the signaling relay: forwarding to the other participant only, the `UNAVAILABLE` reply for an offline callee, the `ANSWER`/`REJECT` echo, the unrelayed `KEEPALIVE`, and every rejection (group conversation, non-participant, client-sent `UNAVAILABLE`, missing payload or `callId`). The WebRTC half can't be unit-tested from here — see [`e2e/video-call.mjs`](../e2e/video-call.mjs), which drives real browsers through a call.

Note: `PushSubscriptionServiceImplTest` uses syntactically-valid (but not secret) EC key material for its p256dh/auth fixtures — the `web-push` library actually parses these as real cryptographic keys, so arbitrary placeholder strings throw deep inside the library rather than failing the assertion you'd expect.

## 🌱 Commit convention

Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`).
