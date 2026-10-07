import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

import type { CallSignal, OutgoingCallSignal } from "@/types/call";
import type { AppNotification, Message, ReadReceiptEvent, TypingEvent } from "@/types/chat";
import type { PresenceEvent } from "@/types/user";

interface SocketCallbacks {
  onMessage: (message: Message) => void;
  onTyping: (event: TypingEvent) => void;
  onPresence: (event: PresenceEvent) => void;
  onReadReceipt: (event: ReadReceiptEvent) => void;
  onNotification: (notification: AppNotification) => void;
  onCallSignal: (signal: CallSignal) => void;
  /** The socket dropped and came back (possibly on another backend instance); anything pushed in between was missed. */
  onReconnect: () => void;
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

  let hasConnectedBefore = false;

  client = new Client({
    webSocketFactory: () => new SockJS(SOCKET_URL),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      if (hasConnectedBefore) callbacks.onReconnect();
      hasConnectedBefore = true;

      subscriptions = [
        client!.subscribe("/user/queue/messages", (msg) => callbacks.onMessage(parseBody(msg))),
        client!.subscribe("/user/queue/typing", (msg) => callbacks.onTyping(parseBody(msg))),
        client!.subscribe("/topic/presence", (msg) => callbacks.onPresence(parseBody(msg))),
        client!.subscribe("/user/queue/read-receipts", (msg) => callbacks.onReadReceipt(parseBody(msg))),
        client!.subscribe("/user/queue/notifications", (msg) => callbacks.onNotification(parseBody(msg))),
        client!.subscribe("/user/queue/calls", (msg) => callbacks.onCallSignal(parseBody(msg))),
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

// publish() throws while the socket is down (e.g. reconnecting after a backend instance went away),
// so every sender checks first: ephemeral frames are dropped, a chat message is reported as unsent.

/** Returns false if the socket is down and nothing was sent, so the caller can keep the draft. */
export function sendChatMessage(conversationId: number, content: string): boolean {
  if (!client?.connected) return false;
  client.publish({
    destination: "/app/chat.send",
    body: JSON.stringify({ conversationId, content }),
  });
  return true;
}

export function sendTyping(conversationId: number, typing: boolean): void {
  if (!client?.connected) return;
  client.publish({
    destination: "/app/chat.typing",
    body: JSON.stringify({ conversationId, typing }),
  });
}

export function sendCallSignal(signal: OutgoingCallSignal): void {
  if (!client?.connected) return;
  client.publish({
    destination: "/app/call.signal",
    body: JSON.stringify(signal),
  });
}
