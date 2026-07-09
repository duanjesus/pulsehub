# CLAUDE.md

Guidance for Claude Code (or any AI coding agent) working in this repository.

## What this is

A monorepo for PulseHub, a real-time communication platform: users sign up, see which of their contacts are **online / away / offline**, and exchange **private 1:1 messages** with a live **typing indicator** and **read receipts** — all pushed over a JWT-authenticated STOMP/WebSocket connection, not polled. New messages also generate a persisted, real-time-pushed **notification** (bell + dashboard card), and users can manage a small **profile** (display name, password, avatar upload).

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

- `/app/chat.send`, `/app/chat.typing` — client → server (`ChatWebSocketController`)
- `/user/queue/messages`, `/user/queue/typing`, `/user/queue/read-receipts`, `/user/queue/notifications` — server → one specific user, via `SimpMessagingTemplate.convertAndSendToUser(email, ...)`. **Never** broadcast a message, typing, read-receipt or notification event to a shared conversation topic — only the relevant participant(s) should ever see them.
- `/topic/presence` — server → everyone. Presence is intentionally public (Slack-workspace style), unlike messages.

Read receipts and notifications are two independent signals, both triggered around the same message but not coupled: `ConversationServiceImpl.markAsRead` pushes a `ReadReceiptEvent` to the *sender* when their message is read (drives the ✓✓ checkmark), while `NotificationServiceImpl.notifyNewMessage` persists+pushes a `Notification` to the *recipient* the moment the message is sent (drives the bell). Marking a conversation's messages as read does **not** mark its notification(s) as read, and vice versa — that's intentional, not an oversight; don't "fix" it by cross-wiring them without discussing it first.

Presence transitions: `ONLINE` on STOMP session connect, `OFFLINE` (+ `lastSeenAt`) on disconnect, `AWAY` after `pulsehub.presence.away-after-minutes` (default 5) of inactivity — flipped by a `@Scheduled` job (`PresenceScheduler`) that runs every 60s, not by a client heartbeat timer. Any inbound chat/typing frame counts as activity and flips `AWAY` back to `ONLINE` immediately (`PresenceService.recordActivity`). If you add a new STOMP destination that represents user activity, call `recordActivity` from it too, or idle users sending only that frame type will incorrectly go `AWAY`.

Conversations are looked up by an **ordered pair** of user ids (`userOneId < userTwoId`, enforced by both a service-layer `Math.min`/`Math.max` and a DB `CHECK` constraint) so a 1:1 conversation is unique regardless of who messages first — don't add a path that constructs a `Conversation` without going through `ConversationService.getOrCreateConversation`.

## Backend conventions (`backend/`)

- Layered architecture: `controller` (REST) / `controller/ws` (STOMP) → `service` (+ `service/impl`) → `repository`, with MapStruct `mapper` interfaces for entity→response DTO conversion only. Anything that composes data across entities (e.g. `ConversationResponse` combining participant + last message + unread count) is assembled by hand in the service layer, not MapStruct.
- Schema is owned by **Flyway** (`backend/src/main/resources/db/migration/V*.sql`), `ddl-auto: validate` — Hibernate never mutates the schema. Adding a column/table means a new `V<n>__description.sql` migration, never editing an already-applied one.
- Custom exceptions (`ResourceNotFoundException`, `DuplicateResourceException`, `BusinessException`, `InvalidCredentialsException`) map to specific HTTP statuses in `GlobalExceptionHandler` — throw the right one rather than a generic exception.
- Tests live in `backend/src/test`, JUnit 5 + Mockito + AssertJ, one test class per service impl that has real branching logic (`AuthServiceImplTest`, `ConversationServiceImplTest`, `PresenceServiceImplTest`, `NotificationServiceImplTest`, `UserServiceImplTest`).
- Commit convention: Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`).
- Avatar uploads are stored on disk under `pulsehub.uploads.dir` (`AvatarStorageServiceImpl`), not in the database — validated for content-type (`image/png|jpeg|webp|gif`) and size (≤5MB) before being written. The property defaults to a relative `uploads` dir, which resolves to `/app/uploads` inside the Docker image (pre-created and chowned to the `spring` user in `backend/Dockerfile`, backed by the `pulsehub-uploads-data` volume) and to `backend/uploads` for local `mvn spring-boot:run`. Served back publicly via the `/uploads/**` resource handler in `WebConfig` — there's no per-user access control on the URL itself, so don't treat it as anything more sensitive than a public avatar.

## Frontend conventions (`frontend/`)

- Vite + React 18 + TypeScript, path alias `@/*` → `frontend/src/*`.
- Server state that's fetched once and can go stale (contacts, conversations, message history, dashboard) goes through TanStack Query hooks in `src/hooks/`. State that arrives continuously over the socket (presence, typing) lives in Zustand stores in `src/store/` instead — don't route socket events through the query cache except for messages themselves (see frontend README's "How real-time state flows into the UI").
- `src/lib/api.ts` holds the Axios instance (JWT interceptor + 401 redirect); `src/lib/ws.ts` holds the STOMP/SockJS client. Nothing else should import `sockjs-client` or `@stomp/stompjs` directly.
- `<ProtectedRoute>` both gates authenticated routes and mounts `useChatSocket()` — the STOMP connection's lifecycle is tied to this one place, not to individual pages.
- Styling is Tailwind utility classes only; the `brand` and `presence` (online/away/offline dot) color scales are defined in `tailwind.config.js`.

## Things to watch for

- `vite.config.ts` sets `define: { global: "window" }` — required because `sockjs-client` references Node's `global`, which doesn't exist in a browser. Removing it breaks the app at import time with `ReferenceError: global is not defined`.
- Any new backend route the frontend fetches as a static asset (like `/uploads/**`) needs its own proxy entry in **both** `frontend/vite.config.ts` (dev) and `frontend/nginx.conf` (prod) — `/api` and `/ws` being proxied does not imply anything else is. This was a real bug caught during V2 verification: avatar `<img>` tags 404'd silently against Vite's own dev server (which falls back to serving `index.html` for unknown paths) until `/uploads` was added to the proxy config — the failure mode is a broken image with no console error and no failed-network-request either, so it's easy to miss without visually checking the rendered page.
- CORS and STOMP `setAllowedOriginPatterns` are both wide open (`"*"`) — fine for this project's current scope, revisit before adding real authz stakes.
- The frontend derives "is this contact typing" and "is this contact online" purely from `store/chatStore.ts` / `store/presenceStore.ts`, keyed by user id — there is no polling fallback, so if the socket connection drops, presence/typing silently go stale until it reconnects (`reconnectDelay: 5000` in `lib/ws.ts`).
