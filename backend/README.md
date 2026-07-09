<div align="center">

# PulseHub — Backend

### Spring Boot backend for a real-time communication platform: JWT auth, private messaging, presence and typing indicators over STOMP/WebSocket

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
   │  DISCONNECT              │                            │
   ├─────────────────────────▶│  status=OFFLINE, lastSeenAt │
   │                          ├───────────────────────────▶│ /topic/presence
```

Messages are always sent to a specific user's private queue via `SimpMessagingTemplate.convertAndSendToUser`, never broadcast to a shared conversation topic — only the two participants ever see them. Presence, by contrast, is public: everyone subscribes to `/topic/presence` (Slack-style workspace, not per-conversation visibility). A user idle for `pulsehub.presence.away-after-minutes` (default 5) while still connected is flipped from `ONLINE` to `AWAY` by a scheduled job (`PresenceScheduler`), and back to `ONLINE` the moment any STOMP frame from that session records activity again.

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
| GET    | `/api/v1/conversations`                 | List the caller's conversations        |
| GET    | `/api/v1/conversations/{id}/messages`   | Paginated message history              |
| POST   | `/api/v1/conversations/{id}/read`       | Mark a conversation's messages as read |
| GET    | `/api/v1/dashboard`                     | Online users, recent chats, unread count |

| STOMP endpoint         | Direction | Description                              |
|------------------------|-----------|--------------------------------------------|
| `/ws` (SockJS)          | —         | Connection endpoint, JWT in the CONNECT header |
| `/app/chat.send`        | client → server | Send a message to `recipientId`     |
| `/app/chat.typing`      | client → server | Notify `recipientId` of typing state |
| `/user/queue/messages`  | server → client | New message for this user           |
| `/user/queue/typing`    | server → client | Typing state from a peer            |
| `/topic/presence`       | server → client | Any user's status changed           |

## 🧪 Tests

```bash
mvn test
```

`AuthServiceImplTest`, `ConversationServiceImplTest` and `PresenceServiceImplTest` cover the registration/login rules, the ordered-pair conversation lookup, and the online/away presence transitions with Mockito — no real database needed.

## 🌱 Commit convention

Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`).
