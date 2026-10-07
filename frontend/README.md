# PulseHub — Frontend

React + TypeScript single-page app that consumes the [backend API](../backend) for auth and history, and holds a single STOMP/WebSocket connection for everything real-time: new messages (text or voice), typing state, presence, read receipts and notifications — for both direct chats and groups. A service worker adds real OS-level push notifications on top, and direct chats can start a 1:1 video call over WebRTC, signaled through that same connection.

> This is the frontend half of the [PulseHub monorepo](../README.md).

## Tech stack

- [Vite](https://vitejs.dev/) + React 18 + TypeScript
- [React Router](https://reactrouter.com/) for client-side routing
- [TanStack Query](https://tanstack.com/query) for server-state (conversations, contacts, message history, notifications, profile, dashboard)
- [Zustand](https://zustand-demo.pmnd.rs/) for the state that arrives over the socket rather than being fetched: live presence, typing indicators and the current call
- WebRTC (`RTCPeerConnection`, no library) for 1:1 video calls
- [@stomp/stompjs](https://stomp-js.github.io/) + [sockjs-client](https://github.com/sockjs/sockjs-client) for the WebSocket connection
- [React Hook Form](https://react-hook-form.com/) + [Zod](https://zod.dev/) for the auth and profile forms
- [Axios](https://axios-http.com/) for HTTP, with a JWT interceptor
- [Tailwind CSS](https://tailwindcss.com/) for styling

## Getting started

```bash
cd frontend
npm install
npm run dev
```

The app runs at `http://localhost:5173`. In dev mode, Vite proxies `/api/*`, `/ws/*` and `/uploads/*` to `http://localhost:8080` (see `vite.config.ts`), so the backend must be running separately (`docker compose up -d db api` from the repo root, or `mvn spring-boot:run` inside `backend/`). The `/uploads` proxy is what makes uploaded avatars actually render in dev — without it `<img>` tags 404 against Vite's own dev server instead of reaching the backend.

## Scripts

| Command           | Description                          |
|--------------------|---------------------------------------|
| `npm run dev`      | Start the Vite dev server             |
| `npm run build`    | Type-check and build for production   |
| `npm run lint`     | Run ESLint                            |
| `npm run preview`  | Preview the production build locally  |

## Direct chats and groups share one model

`Conversation` is a single type with a `type: "DIRECT" | "GROUP"` discriminator and a `participants: ParticipantSummary[]` array (each with a `role: "OWNER" | "MEMBER"`) — there's no separate "contact chat" concept in the UI layer. `ChatPage` routes by `?conversation=<id>`; the one exception is `?with=<contactId>`, a shortcut used by the Dashboard's online-users list (which only knows a user id, not a conversation) that resolves-or-creates the direct conversation and then redirects to `?conversation=<id>`.

Message read receipts generalize the same way: every message carries `readBy: number[]` (the user ids who've read it, excluding the sender). For a DIRECT conversation that's 0 or 1 entries, rendered as `✓`/`✓✓`; for a GROUP it's rendered as `Read N/M` against the other active participants' count.

## Voice messages

`ConversationPanel` records audio with the browser's `MediaRecorder` API (feature-detected — falls back to an inline error if unsupported) and uploads the resulting blob via `useSendVoiceMessage` (`POST /conversations/{id}/messages/voice`, multipart). A message's `type` is `"TEXT"` or `"VOICE"`; a `VOICE` message has `content: null` and instead carries `attachmentUrl`/`attachmentDurationSeconds`, rendered as a native `<audio controls>` player plus a duration label. There's no separate voice-message store — it flows through the exact same `messagesQueryKey` cache and `/user/queue/messages` WebSocket push as a text message.

## Push notifications

`lib/push.ts` wraps the browser's Push API: `subscribeToPush()` registers `public/sw.js`, requests `Notification` permission, subscribes via `PushManager` using the backend's VAPID public key (`GET /push/vapid-public-key`), and posts the resulting subscription (`endpoint` + `keys.p256dh` + `keys.auth`) to `POST /push/subscribe`. The toggle lives on the Profile page and reflects the current subscription state on load via `getExistingSubscription()`. `public/sw.js` itself just renders whatever `{ title, body, relatedConversationId }` payload the backend sends on a `push` event, and on `notificationclick` focuses (or opens) the app at `/chat?conversation=<relatedConversationId>`. This is independent of the in-app notification bell — a user can have one, both, or neither enabled.

## Video calls

The camera button in a direct conversation's header starts a 1:1 call; group conversations don't have one. The pieces:

- **`lib/call.ts`** is the call engine and the only module that touches `RTCPeerConnection` and `getUserMedia`. It exposes `startCall`, `acceptCall`, `declineCall`, `hangUp`, `toggleMic`, `toggleCamera` and `handleCallSignal` (fed by `useChatSocket` from `/user/queue/calls`).
- **`store/callStore.ts`** holds what the UI renders: the phase (`idle` → `outgoing`/`incoming` → `connecting` → `active`), the peer, both `MediaStream`s and the mute/camera flags. Only `lib/call.ts` writes to it.
- **`components/call/CallOverlay.tsx`** is the whole UI: the incoming-call prompt, the in-call screen (remote video, local preview, mute/camera/hang-up) and a one-line outcome afterwards ("Grace declined the call."). It's mounted once in `AppLayout`, so a call survives navigating between pages.

Things the engine handles that are easy to get wrong:

- **Candidates before descriptions.** ICE candidates are buffered per `callId` until the remote description is set. On the callee that's always the case (they arrive while it's still ringing), and the server doesn't guarantee frame order, so a candidate can even overtake its own `OFFER`.
- **No camera.** If the camera is missing or held by another app, the call falls back to voice only. A camera-less caller still adds a receive-only video line to the offer, so it can see the other side.
- **Every async step re-checks the call is still current.** The user can hang up, or the peer can cancel, while the permission prompt is open; the freshly-opened stream is stopped instead of leaking a live camera.
- **Other tabs.** The server echoes `ANSWER`/`REJECT` to the sender's own sessions, so a second tab of the same account stops ringing once one tab has answered.

`getUserMedia` only exists in a secure context: calls work on `https://` and on `http://localhost`, and nowhere else. ICE servers are fetched from `GET /calls/ice-servers` rather than hardcoded. There's no TURN server configured, so peers behind strict NATs won't connect — see the backend README.

## How real-time state flows into the UI

`useChatSocket` (mounted once, inside `ProtectedRoute`) owns the single STOMP connection for the whole app and fans incoming frames out in two directions:

- **New messages** (`/user/queue/messages`) are written straight into the TanStack Query cache for that conversation, and invalidate the conversations list and dashboard so unread counts and previews stay correct — no polling.
- **Read receipts** (`/user/queue/read-receipts`) patch the cached message list directly, appending the reader's id to every qualifying message's `readBy` array so the checkmark/count updates live in `ConversationPanel`, without waiting for a refetch.
- **Notifications** (`/user/queue/notifications`) are prepended into the notifications query cache and bump the unread-count cache, so the bell badge and the dashboard's Notifications card update instantly.
- **Typing** (`/user/queue/typing`) and **presence** (`/topic/presence`) events update the `chatStore` / `presenceStore` Zustand stores, keyed by `conversationId` (typing) or `userId` (presence) respectively. Presence and typing are deliberately kept out of TanStack Query since they change far more often than the underlying data — components read the base conversation/contact from the query cache and overlay live state from the stores.

- **Call signals** (`/user/queue/calls`) are handed to `handleCallSignal` in `lib/call.ts`, which drives the `callStore` — see "Video calls" above.

`lib/ws.ts` wraps `@stomp/stompjs` + `sockjs-client` into a handful of functions (`connectSocket`, `disconnectSocket`, `sendChatMessage`, `sendTyping`, `sendCallSignal`) — nothing else in the app touches STOMP directly.

**Losing the socket is normal, not exceptional.** Behind a load balancer a backend instance can go away at any time; the STOMP client reconnects on its own (5s delay) and may land on a different instance. Two things follow. Frames pushed during the gap are gone, so on every *re*connect `useChatSocket` clears the presence overlay and invalidates every query, letting REST bring the UI back up to date. And `publish()` throws while disconnected, so each sender in `lib/ws.ts` checks first: typing and call signals are dropped, while `sendChatMessage` returns `false` and `ConversationPanel` keeps the draft and says the message wasn't sent.

## The load balancer

In the Docker image, [`nginx.conf`](nginx.conf) does more than serve the SPA: it is the load balancer in front of the API replicas. It re-resolves the `api` service through Docker's DNS (so `docker compose up -d --scale api=3` takes effect without a reload), round-robins REST calls, and hashes `/ws/` traffic on the SockJS session id so the several HTTP requests of a fallback transport all reach the instance holding that session. It also listens on 8080 to expose the balanced API directly (Swagger, curl, other services). The Vite dev server is unaffected — it proxies to a single backend on `localhost:8080`.

Note that reading a conversation and reading a notification are independent actions: opening a chat marks its *messages* as read (and notifies the other participants), but does not mark the corresponding *notification* as read — that only happens when the bell dropdown or the dashboard's Notifications card is clicked. This mirrors the backend, which tracks the two as separate entities on purpose.

**Zustand gotcha worth knowing:** never call a store method that computes and returns a new array/object from inside a `useStore(state => ...)` selector — each call returns a different reference, and `useSyncExternalStore` (which Zustand v5 uses internally) will loop and crash the component with "The result of getSnapshot should be cached." Select the raw state slice instead and derive with `useMemo` in the component (see `typingRecord`/`typingUserIds` in `ConversationPanel.tsx`).

## Structure

```
src/
├── components/
│   ├── call/        # CallOverlay (incoming prompt, in-call screen)
│   ├── layout/      # AppLayout, Sidebar, NotificationBell
│   └── ui/          # Button, Input, Avatar, PresenceDot, EmptyState, Spinner, ErrorBanner
├── context/         # AuthContext (JWT session, current user)
├── store/           # presenceStore, chatStore, callStore (Zustand — realtime state, not fetched)
├── hooks/           # useUsers, useConversations (+ direct/group/membership mutations),
│                     # useMessages (+ useSendVoiceMessage), useNotifications, useProfile,
│                     # useDashboard (TanStack Query) + useChatSocket (owns the STOMP lifecycle)
├── lib/             # Axios instance + interceptors, STOMP/SockJS client, Web Push (push.ts),
│                     # WebRTC call engine (call.ts), QueryClient
├── pages/
│   ├── auth/        # LoginPage, RegisterPage
│   ├── dashboard/   # DashboardPage (online users, recent conversations, unread counts, notifications)
│   ├── chat/         # ChatPage + ConversationList + ConversationPanel + NewGroupModal
│   └── profile/     # ProfilePage (avatar upload, display name, password change)
└── types/           # TypeScript types mirroring the backend DTOs
```
