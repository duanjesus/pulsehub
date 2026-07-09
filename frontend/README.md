# PulseHub — Frontend

React + TypeScript single-page app that consumes the [backend API](../backend) for auth and history, and holds a single STOMP/WebSocket connection for everything real-time: new messages, typing state and presence.

> This is the frontend half of the [PulseHub monorepo](../README.md).

## Tech stack

- [Vite](https://vitejs.dev/) + React 18 + TypeScript
- [React Router](https://reactrouter.com/) for client-side routing
- [TanStack Query](https://tanstack.com/query) for server-state (contacts, conversations, message history, dashboard)
- [Zustand](https://zustand-demo.pmnd.rs/) for the two pieces of state that arrive over the socket rather than being fetched: live presence and typing indicators
- [@stomp/stompjs](https://stomp-js.github.io/) + [sockjs-client](https://github.com/sockjs/sockjs-client) for the WebSocket connection
- [React Hook Form](https://react-hook-form.com/) + [Zod](https://zod.dev/) for the auth forms
- [Axios](https://axios-http.com/) for HTTP, with a JWT interceptor
- [Tailwind CSS](https://tailwindcss.com/) for styling

## Getting started

```bash
cd frontend
npm install
npm run dev
```

The app runs at `http://localhost:5173`. In dev mode, Vite proxies `/api/*` and `/ws/*` to `http://localhost:8080` (see `vite.config.ts`), so the backend must be running separately (`docker compose up -d db api` from the repo root, or `mvn spring-boot:run` inside `backend/`).

## Scripts

| Command           | Description                          |
|--------------------|---------------------------------------|
| `npm run dev`      | Start the Vite dev server             |
| `npm run build`    | Type-check and build for production   |
| `npm run lint`     | Run ESLint                            |
| `npm run preview`  | Preview the production build locally  |

## How real-time state flows into the UI

`useChatSocket` (mounted once, inside `ProtectedRoute`) owns the single STOMP connection for the whole app and fans incoming frames out in two directions:

- **New messages** (`/user/queue/messages`) are written straight into the TanStack Query cache for that conversation, and invalidate the conversations list and dashboard so unread counts and previews stay correct — no polling.
- **Typing** (`/user/queue/typing`) and **presence** (`/topic/presence`) events update the `chatStore` / `presenceStore` Zustand stores. Presence is deliberately kept out of TanStack Query: it changes far more often than the contact list itself, so components read the base contact from the query cache and overlay live status from `presenceStore`.

`lib/ws.ts` wraps `@stomp/stompjs` + `sockjs-client` into four functions (`connectSocket`, `disconnectSocket`, `sendChatMessage`, `sendTyping`) — nothing else in the app touches STOMP directly.

## Structure

```
src/
├── components/
│   ├── layout/      # AppLayout, Sidebar
│   └── ui/          # Button, Input, Avatar, PresenceDot, EmptyState, Spinner, ErrorBanner
├── context/         # AuthContext (JWT session, current user)
├── store/           # presenceStore, chatStore (Zustand — realtime state, not fetched)
├── hooks/           # useUsers, useConversations, useMessages, useDashboard (TanStack Query)
│                     # + useChatSocket (owns the STOMP connection lifecycle)
├── lib/             # Axios instance + interceptors, STOMP/SockJS client, QueryClient
├── pages/
│   ├── auth/        # LoginPage, RegisterPage
│   ├── dashboard/   # DashboardPage (online users, recent conversations, unread count)
│   └── chat/        # ChatPage + ContactList + ConversationPanel
└── types/           # TypeScript types mirroring the backend DTOs
```
