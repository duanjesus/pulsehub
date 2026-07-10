# CLAUDE.md

Guidance for Claude Code (or any AI coding agent) working in this repository.

## What this is

A monorepo for PulseHub, a real-time communication platform: users sign up, see which of their contacts are **online / away / offline**, and exchange **text or voice messages** in **1:1 direct chats or named groups** with a live **typing indicator** and **read receipts** ("Read N/M" for a group's other members) — all pushed over a JWT-authenticated STOMP/WebSocket connection, not polled. New messages also generate a persisted, real-time-pushed **notification** (bell + dashboard card) for every recipient, plus a real **Web Push** notification via a service worker (delivered even with the tab closed), and users can manage a small **profile** (display name, password, avatar upload).

```
pulsehub/
├── backend/    Spring Boot 3 API (Java 21, WebSocket/STOMP, PostgreSQL, Flyway, JWT auth)
├── frontend/   React + TypeScript SPA (Vite, Tailwind, TanStack Query, Zustand, STOMP.js/SockJS)
├── docker-compose.yml   Orchestrates db + api + web
└── .github/workflows/ci.yml   Two jobs: backend (Maven), frontend (npm)
```

Each package has its own `README.md` with full details — [backend/README.md](backend/README.md), [frontend/README.md](frontend/README.md). This file focuses on cross-cutting context an agent needs before making changes.

## Running things

```bash
cd frontend && npm ci && npm run lint && npm run build   # or: npm run dev
cd backend && mvn -B clean test                            # or: mvn spring-boot:run
```

`frontend/package-lock.json` is committed — CI uses `npm ci` with the lockfile cached. If you add/bump a dependency, run `npm install` locally and commit the updated lockfile.

Full stack via Docker (from repo root): `docker compose up --build` — frontend at `:3000`, API at `:8080`, Swagger at `:8080/swagger-ui.html`. `docker-compose.yml` here and the sibling `cashpilot`/`social-supply-management-api` repos all default to the same host ports (5432/8080/3000), so only one of the three stacks can run at a time without remapping ports.

**No Java or Node were available in this project's original setup environment.** The backend was verified by building/running it inside its own Docker image (`docker compose up --build db api`), reading the Flyway/Spring Boot startup logs, and hitting the REST endpoints with `curl`. The frontend was verified with a local Node install at `C:\Program Files\nodejs` (not on `PATH` by default) via `npm run build`/`npm run lint` and the Vite dev server. If `mvn`/`npm` appear missing, check for a local install before assuming the toolchain needs to be added.

## The contract between frontend and backend

The frontend has **no backend code of its own** — it's a pure client of the API and the STOMP broker. When you change a backend DTO, controller route, STOMP destination, or enum, the matching frontend type/hook/store almost certainly needs updating too, and vice versa.

| Backend source of truth | Frontend mirror |
|---|---|
| `backend/.../dto/request/*.java` | `frontend/src/types/*.ts` request payloads |
| `backend/.../dto/response/*.java` | `frontend/src/types/*.ts` response shapes |
| `backend/.../entity/enums/UserStatus.java` | `frontend/src/types/user.ts` (`UserStatus` union + `STATUS_LABELS`) |
| `backend/.../controller/*.java` (REST) | `frontend/src/hooks/use*.ts` |
| `backend/.../controller/ws/ChatWebSocketController.java` (STOMP destinations) | `frontend/src/lib/ws.ts` |
| `backend/.../entity/enums/NotificationType.java` | `frontend/src/types/chat.ts` (`NotificationType`) |
| `GlobalExceptionHandler` → `ErrorResponse` shape | `frontend/src/types/common.ts` (`ApiErrorResponse`) + `lib/api.ts` (`extractErrorMessage`) |

API base path: `/api/v1`. All REST routes require a JWT (`Authorization: Bearer <token>`) except `/api/v1/auth/**`, `/uploads/**` and Swagger. There is no admin/role split — every authenticated user has the same single `ROLE_USER` authority, and every other registered user is a visible "contact" (`GET /api/v1/users`); there is no friend-request/approval flow.

### Real-time is STOMP, not raw WebSocket

The STOMP endpoint is `/ws` (SockJS fallback enabled). The CONNECT frame's `Authorization` header carries the same JWT as REST calls — `WebSocketAuthChannelInterceptor` (`backend/.../security/`) validates it and attaches a `Principal` (the user's email) to the session, exactly per Spring's documented pattern for STOMP JWT auth. Every later frame on that session is authenticated for free because of this — don't re-validate per-message.

- `/app/chat.send`, `/app/chat.typing` — client → server (`ChatWebSocketController`), payload carries a `conversationId` (never a `recipientId` — that concept doesn't exist since V3, since a group has no single recipient).
- `/user/queue/messages`, `/user/queue/typing`, `/user/queue/read-receipts`, `/user/queue/notifications` — server → one specific user, via `SimpMessagingTemplate.convertAndSendToUser(email, ...)`, looped over **every currently-active participant** of the conversation (`ConversationService.getActiveParticipants`). **Never** broadcast a message, typing, read-receipt or notification event to a shared conversation topic — only current participants should ever see them, and a removed/left member must stop receiving immediately.
- `/topic/presence` — server → everyone. Presence is intentionally public (Slack-workspace style), unlike messages.

Read receipts and notifications are two independent signals, both triggered around the same message but not coupled: `ConversationServiceImpl.markAsRead` pushes a `ReadReceiptEvent` to *every other active participant* when their message is read (drives the ✓✓/`Read N/M` indicator), while `ChatWebSocketController.sendMessage` calls `NotificationServiceImpl.notifyNewMessage` once per recipient the moment the message is sent (drives the bell). Marking a conversation's messages as read does **not** mark its notification(s) as read, and vice versa — that's intentional, not an oversight; don't "fix" it by cross-wiring them without discussing it first.

Presence transitions: `ONLINE` on STOMP session connect, `OFFLINE` (+ `lastSeenAt`) on disconnect, `AWAY` after `pulsehub.presence.away-after-minutes` (default 5) of inactivity — flipped by a `@Scheduled` job (`PresenceScheduler`) that runs every 60s, not by a client heartbeat timer. Any inbound chat/typing frame counts as activity and flips `AWAY` back to `ONLINE` immediately (`PresenceService.recordActivity`). If you add a new STOMP destination that represents user activity, call `recordActivity` from it too, or idle users sending only that frame type will incorrectly go `AWAY`.

### Direct vs. group conversations (V3)

`Conversation.type` is `DIRECT` or `GROUP`; the actual roster lives in `ConversationParticipant` (`conversation_id`, `user_id`, `role` `OWNER`/`MEMBER`, `joined_at`, `left_at`), not on the conversation itself. A `DIRECT` conversation is deduplicated via `directKey` (`"<lowerUserId>_<higherUserId>"`, unique per type) — go through `ConversationService.getOrCreateDirectConversation`, never construct one by hand. Message read state lives in its own `MessageRead` table (`message_id`, `user_id`, `read_at`), exposed to the API as `readBy: number[]` per message — this is what lets a group message be "read by 2 of 4" instead of a single boolean. Only a `GROUP`'s `OWNER` can add/remove members; anyone can leave, and if the owner leaves, the longest-tenured remaining member is auto-promoted (`ConversationServiceImpl.leaveConversation`) so a group is never left ownerless. See `backend/README.md`'s "Data model" section and migration `V5__add_group_conversations.sql` for the full schema and how V1/V2 data was backfilled.

### Voice messages and Web Push (V4)

`Message.type` is `TEXT` or `VOICE` (DB `CHECK` constraint enforces `content`/`attachmentUrl` are set exclusively). Both go through `MessageDispatchService` — the single place that persists a message, broadcasts it to every active participant, and fans out notifications — shared by `ChatWebSocketController`'s STOMP text path and `ConversationController.sendVoiceMessage`'s REST path. **If you add a third way to send a message, route it through `MessageDispatchService` too** rather than duplicating the persist+broadcast+notify logic a third time.

Web Push is best-effort and independent of the in-app notification: `NotificationServiceImpl.notifyNewMessage` calls `PushSubscriptionService.sendPush` after creating the persisted `Notification`, catches everything, and prunes the `PushSubscription` row on a `410`/`404` response. VAPID key material lives in `pulsehub.push.vapid.*`. See `backend/README.md`'s "Voice messages and Web Push" section for the full VAPID/BouncyCastle story.

## Backend conventions (`backend/`)

- Layered architecture: `controller` (REST) / `controller/ws` (STOMP) → `service` (+ `service/impl`) → `repository`, with MapStruct `mapper` interfaces (`UserMapper`, `NotificationMapper`) for straightforward entity→response DTO conversion only. Anything that composes data across entities or tables is assembled by hand in the service layer instead — `ConversationResponse` (participants + last message + unread count) and `MessageResponse.readBy` (a batch query against `MessageRead`, grouped in memory rather than N+1'd) are both built manually in `ConversationServiceImpl`; there's deliberately no `MessageMapper` since a `Message` entity alone can't produce a correct `readBy`.
- Schema is owned by **Flyway** (`backend/src/main/resources/db/migration/V*.sql`), `ddl-auto: validate` — Hibernate never mutates the schema. Adding a column/table means a new `V<n>__description.sql` migration, never editing an already-applied one.
- Custom exceptions (`ResourceNotFoundException`, `DuplicateResourceException`, `BusinessException`, `InvalidCredentialsException`) map to specific HTTP statuses in `GlobalExceptionHandler` — throw the right one rather than a generic exception.
- Tests live in `backend/src/test`, JUnit 5 + Mockito + AssertJ, one test class per service impl that has real branching logic (`AuthServiceImplTest`, `ConversationServiceImplTest`, `PresenceServiceImplTest`, `NotificationServiceImplTest`, `UserServiceImplTest`, `MessageDispatchServiceImplTest`, `PushSubscriptionServiceImplTest`, `AudioStorageServiceImplTest`).
- Commit convention: Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`).
- Avatar uploads are stored on disk under `pulsehub.uploads.dir` (`AvatarStorageServiceImpl`), not in the database — validated for content-type (`image/png|jpeg|webp|gif`) and size (≤5MB) before being written. The property defaults to a relative `uploads` dir, which resolves to `/app/uploads` inside the Docker image (pre-created and chowned to the `spring` user in `backend/Dockerfile`, backed by the `pulsehub-uploads-data` volume) and to `backend/uploads` for local `mvn spring-boot:run`. Served back publicly via the `/uploads/**` resource handler in `WebConfig` — there's no per-user access control on the URL itself, so don't treat it as anything more sensitive than a public avatar.

## Frontend conventions (`frontend/`)

- Vite + React 18 + TypeScript, path alias `@/*` → `frontend/src/*`.
- Server state that's fetched once and can go stale (contacts, conversations, message history, dashboard) goes through TanStack Query hooks in `src/hooks/`. State that arrives continuously over the socket (presence, typing) lives in Zustand stores in `src/store/` instead — don't route socket events through the query cache except for messages themselves (see frontend README's "How real-time state flows into the UI").
- `src/lib/api.ts` holds the Axios instance (JWT interceptor + 401 redirect); `src/lib/ws.ts` holds the STOMP/SockJS client. Nothing else should import `sockjs-client` or `@stomp/stompjs` directly.
- `<ProtectedRoute>` both gates authenticated routes and mounts `useChatSocket()` — the STOMP connection's lifecycle is tied to this one place, not to individual pages.
- Styling is Tailwind utility classes only; the `brand` and `presence` (online/away/offline dot) color scales are defined in `tailwind.config.js`.

## Things to watch for

- **Registering a security provider in a `static` block on only one class doesn't guarantee it runs before another class needs it.** Real bug hit during V4: `nl.martijndwars:web-push` parses VAPID EC keys via `KeyFactory.getInstance("EC", "BC")`, requiring the BouncyCastle provider to be registered first. It was registered only in `PushSubscriptionServiceImpl`'s static initializer — Mockito unit tests passed (they instantiate that class directly, triggering it), but the real Spring app **crashed on startup** with `NoSuchProviderException: no such provider: BC`, because `WebPushConfig`'s `@Bean` method (which also needs BC, to build the shared `PushService`) got instantiated by Spring before `PushSubscriptionServiceImpl`'s class ever loaded. Fixed by registering BC in **both** static blocks (harmless — `Security.addProvider` on an already-registered provider is a no-op). Lesson: unit tests instantiating a class directly don't catch bean-initialization-order bugs that only show up when the full Spring context boots — always smoke-test via `docker compose up --build db api` (or equivalent) after adding a library with global JVM-level registration/init requirements, don't stop at green unit tests.
- **Never call a Zustand store method that returns a freshly-computed array/object from inside a `useStore(state => ...)` selector.** Each call returns a new reference, and `useSyncExternalStore` (which Zustand v5 uses internally) treats that as "the snapshot keeps changing," logging "The result of getSnapshot should be cached to avoid an infinite loop" and crashing the component. Hit this for real during V3: `chatStore` originally exposed `getTypingUserIds(conversationId)` computed via `Object.keys(...).map(Number)` inside the store, called as `useChatStore(state => state.getTypingUserIds(id))` — infinite loop. Fixed by selecting the raw `typingByConversation[id]` record and deriving the array with `useMemo` in the component instead (see `ConversationPanel.tsx`). If a component using a Zustand selector crashes with that exact warning, this is almost certainly why — check for a derived/computed value being returned from inside the selector before looking anywhere else. Also: this failure mode can survive a plain `window.location.reload()` in dev (React Fast Refresh can preserve a wedged fiber tree) — if a fix doesn't seem to take effect after reload, restart the dev server itself before concluding the fix is wrong.
- `vite.config.ts` sets `define: { global: "window" }` — required because `sockjs-client` references Node's `global`, which doesn't exist in a browser. Removing it breaks the app at import time with `ReferenceError: global is not defined`.
- Any new backend route the frontend fetches as a static asset (like `/uploads/**`) needs its own proxy entry in **both** `frontend/vite.config.ts` (dev) and `frontend/nginx.conf` (prod) — `/api` and `/ws` being proxied does not imply anything else is. This was a real bug caught during V2 verification: avatar `<img>` tags 404'd silently against Vite's own dev server (which falls back to serving `index.html` for unknown paths) until `/uploads` was added to the proxy config — the failure mode is a broken image with no console error and no failed-network-request either, so it's easy to miss without visually checking the rendered page.
- CORS and STOMP `setAllowedOriginPatterns` are both wide open (`"*"`) — fine for this project's current scope, revisit before adding real authz stakes.
- The frontend derives "is this contact online" from `store/presenceStore.ts` (keyed by user id) and "is someone typing in this conversation" from `store/chatStore.ts` (keyed by conversation id, then user id) — there is no polling fallback, so if the socket connection drops, presence/typing silently go stale until it reconnects (`reconnectDelay: 5000` in `lib/ws.ts`).
- `ChatPage` supports two query params on purpose: `?conversation=<id>` opens a known conversation directly (used by the conversation list, notifications, and the dashboard's "recent conversations"), while `?with=<contactId>` is a shortcut for "start or open a direct chat with this user" (used by the dashboard's online-users list, which only has a user id, no conversation). The `?with` handler resolves-or-creates the direct conversation then replaces the URL with `?conversation=<id>` — don't add a third pattern; extend one of these two.
