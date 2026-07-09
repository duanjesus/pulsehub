<div align="center">

# PulseHub — Backend

### Spring Boot backend for a real-time communication platform: JWT auth, private messaging, presence, typing indicators, read receipts and notifications over STOMP/WebSocket

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
   │ SEND /app/chat.send      │                            │
   ├─────────────────────────▶│  persist Message            │
   │                          │  convertAndSendToUser(B) ──▶│ /user/queue/messages
   │                          │  convertAndSendToUser(A) ──▶│ (echo, for multi-tab sync)
   │                          │                            │
   │ SEND /app/chat.typing    │                            │
   ├─────────────────────────▶│  convertAndSendToUser(B) ──▶│ /user/queue/typing
   │                          │                            │
   │ POST /conversations/1/read (B reads A's message)       │
   ├─────────────────────────▶│  readAt set on the message  │
   │                          │  convertAndSendToUser(A) ──▶│ /user/queue/read-receipts
   │                          │  (A's sent message flips to "read" live)
   │                          │                            │
   │  DISCONNECT              │                            │
   ├─────────────────────────▶│  status=OFFLINE, lastSeenAt │
   │                          ├───────────────────────────▶│ /topic/presence
```

Messages are always sent to a specific user's private queue via `SimpMessagingTemplate.convertAndSendToUser`, never broadcast to a shared conversation topic — only the two participants ever see them. Presence, by contrast, is public: everyone subscribes to `/topic/presence` (Slack-style workspace, not per-conversation visibility). A user idle for `pulsehub.presence.away-after-minutes` (default 5) while still connected is flipped from `ONLINE` to `AWAY` by a scheduled job (`PresenceScheduler`), and back to `ONLINE` the moment any STOMP frame from that session records activity again.

Every new message also creates a persisted `Notification` row for the recipient (`NotificationService.notifyNewMessage`) and pushes it to `/user/queue/notifications` if they're connected — this is what powers the notification bell and the dashboard's Notifications card; it's independent of message read state (reading a conversation does not automatically mark its notification as read, and vice versa).

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
| GET    | `/api/v1/conversations`                 | List the caller's conversations        |
| GET    | `/api/v1/conversations/{id}/messages`   | Paginated message history              |
| POST   | `/api/v1/conversations/{id}/read`       | Mark a conversation's messages as read, notifies the sender |
| GET    | `/api/v1/notifications`                 | Paginated notification history         |
| GET    | `/api/v1/notifications/unread-count`    | Unread notification count              |
| POST   | `/api/v1/notifications/{id}/read`       | Mark one notification as read          |
| POST   | `/api/v1/notifications/read-all`        | Mark every notification as read        |
| GET    | `/api/v1/dashboard`                     | Online users, recent chats, unread counts, recent notifications |

| STOMP endpoint            | Direction | Description                              |
|----------------------------|-----------|--------------------------------------------|
| `/ws` (SockJS)              | —         | Connection endpoint, JWT in the CONNECT header |
| `/app/chat.send`            | client → server | Send a message to `recipientId`     |
| `/app/chat.typing`          | client → server | Notify `recipientId` of typing state |
| `/user/queue/messages`      | server → client | New message for this user           |
| `/user/queue/typing`        | server → client | Typing state from a peer            |
| `/user/queue/read-receipts` | server → client | A conversation you sent messages in was just read |
| `/user/queue/notifications` | server → client | A new notification was created for you |
| `/topic/presence`           | server → client | Any user's status changed           |

Uploaded avatars are served back as static files from `/uploads/**` (public, no JWT required — `<img>` tags can't attach an Authorization header) and stored on the `pulsehub-uploads-data` Docker volume so they survive container restarts; see `pulsehub.uploads.dir` in `application.yml` and `AvatarStorageServiceImpl`.

## 🧪 Tests

```bash
mvn test
```

`AuthServiceImplTest`, `ConversationServiceImplTest`, `PresenceServiceImplTest`, `NotificationServiceImplTest` and `UserServiceImplTest` cover the registration/login rules, the ordered-pair conversation lookup and read-receipt broadcast, the online/away presence transitions, notification creation/push, and profile updates — all with Mockito, no real database needed.

## 🌱 Commit convention

Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`).
