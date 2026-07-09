import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

import type { Message, TypingEvent } from "@/types/chat";
import type { PresenceEvent } from "@/types/user";

interface SocketCallbacks {
  onMessage: (message: Message) => void;
  onTyping: (event: TypingEvent) => void;
  onPresence: (event: PresenceEvent) => void;
}

const SOCKET_URL = import.meta.env.VITE_WS_BASE_URL ?? "/ws";

let client: Client | null = null;
let subscriptions: StompSubscription[] = [];

function parseBody<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}

export function connectSocket(token: string, callbacks: SocketCallbacks): void {
  if (client?.active) {
    return;
  }

  client = new Client({
    webSocketFactory: () => new SockJS(SOCKET_URL),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      subscriptions = [
        client!.subscribe("/user/queue/messages", (msg) => callbacks.onMessage(parseBody(msg))),
        client!.subscribe("/user/queue/typing", (msg) => callbacks.onTyping(parseBody(msg))),
        client!.subscribe("/topic/presence", (msg) => callbacks.onPresence(parseBody(msg))),
      ];
    },
  });

  client.activate();
}

export function disconnectSocket(): void {
  subscriptions.forEach((sub) => sub.unsubscribe());
  subscriptions = [];
  client?.deactivate();
  client = null;
}

export function sendChatMessage(recipientId: number, content: string): void {
  client?.publish({
    destination: "/app/chat.send",
    body: JSON.stringify({ recipientId, content }),
  });
}

export function sendTyping(recipientId: number, typing: boolean): void {
  client?.publish({
    destination: "/app/chat.typing",
    body: JSON.stringify({ recipientId, typing }),
  });
}
