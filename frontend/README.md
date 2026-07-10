# PulseHub — Frontend

React + TypeScript single-page app that consumes the [backend API](../backend) for auth and history, and holds a single STOMP/WebSocket connection for everything real-time: new messages (text or voice), typing state, presence, read receipts and notifications — for both direct chats and groups. A service worker adds real OS-level push notifications on top.

> This is the frontend half of the [PulseHub monorepo](../README.md).

## Tech stack

- [Vite](https://vitejs.dev/) + React 18 + TypeScript
- [React Router](https://reactrouter.com/) for client-side routing
- [TanStack Query](https://tanstack.com/query) for server-state (conversations, contacts, message history, notifications, profile, dashboard)
- [Zustand](https://zustand-demo.pmnd.rs/) for the two pieces of state that arrive over the socket rather than being fetched: live presence and typing indicators
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

## How real-time state flows into the UI

`useChatSocket` (mounted once, inside `ProtectedRoute`) owns the single STOMP connection for the whole app and fans incoming frames out in two directions:

- **New messages** (`/user/queue/messages`) are written straight into the TanStack Query cache for that conversation, and invalidate the conversations list and dashboard so unread counts and previews stay correct — no polling.
- **Read receipts** (`/user/queue/read-receipts`) patch the cached message list directly, appending the reader's id to every qualifying message's `readBy` array so the checkmark/count updates live in `ConversationPanel`, without waiting for a refetch.
- **Notifications** (`/user/queue/notifications`) are prepended into the notifications query cache and bump the unread-count cache, so the bell badge and the dashboard's Notifications card update instantly.
- **Typing** (`/user/queue/typing`) and **presence** (`/topic/presence`) events update the `chatStore` / `presenceStore` Zustand stores, keyed by `conversationId` (typing) or `userId` (presence) respectively. Presence and typing are deliberately kept out of TanStack Query since they change far more often than the underlying data — components read the base conversation/contact from the query cache and overlay live state from the stores.

`lib/ws.ts` wraps `@stomp/stompjs` + `sockjs-client` into a handful of functions (`connectSocket`, `disconnectSocket`, `sendChatMessage`, `sendTyping`) — nothing else in the app touches STOMP directly.

Note that reading a conversation and reading a notification are independent actions: opening a chat marks its *messages* as read (and notifies the other participants), but does not mark the corresponding *notification* as read — that only happens when the bell dropdown or the dashboard's Notifications card is clicked. This mirrors the backend, which tracks the two as separate entities on purpose.

**Zustand gotcha worth knowing:** never call a store method that computes and returns a new array/object from inside a `useStore(state => ...)` selector — each call returns a different reference, and `useSyncExternalStore` (which Zustand v5 uses internally) will loop and crash the component with "The result of getSnapshot should be cached." Select the raw state slice instead and derive with `useMemo` in the component (see `typingRecord`/`typingUserIds` in `ConversationPanel.tsx`).

## Structure

```
src/
├── components/
│   ├── layout/      # AppLayout, Sidebar, NotificationBell
│   └── ui/          # Button, Input, Avatar, PresenceDot, EmptyState, Spinner, ErrorBanner
├── context/         # AuthContext (JWT session, current user)
├── store/           # presenceStore, chatStore (Zustand — realtime state, not fetched)
├── hooks/           # useUsers, useConversations (+ direct/group/membership mutations),
│                     # useMessages (+ useSendVoiceMessage), useNotifications, useProfile,
│                     # useDashboard (TanStack Query) + useChatSocket (owns the STOMP lifecycle)
├── lib/             # Axios instance + interceptors, STOMP/SockJS client, Web Push (push.ts), QueryClient
├── pages/
│   ├── auth/        # LoginPage, RegisterPage
│   ├── dashboard/   # DashboardPage (online users, recent conversations, unread counts, notifications)
│   ├── chat/         # ChatPage + ConversationList + ConversationPanel + NewGroupModal
│   └── profile/     # ProfilePage (avatar upload, display name, password change)
└── types/           # TypeScript types mirroring the backend DTOs
```
