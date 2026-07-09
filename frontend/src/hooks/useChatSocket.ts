import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { useAuth } from "@/context/AuthContext";
import { connectSocket, disconnectSocket } from "@/lib/ws";
import { usePresenceStore } from "@/store/presenceStore";
import { useChatStore } from "@/store/chatStore";
import { CONVERSATIONS_QUERY_KEY } from "@/hooks/useConversations";
import { messagesQueryKey } from "@/hooks/useMessages";
import { NOTIFICATIONS_QUERY_KEY, UNREAD_NOTIFICATIONS_QUERY_KEY } from "@/hooks/useNotifications";
import type { AppNotification, Message } from "@/types/chat";

/**
 * Owns the single STOMP connection for the whole app — mount once near the
 * root of the authenticated tree. Incoming frames are fanned out into the
 * TanStack Query cache (messages/conversations/dashboard/notifications) and
 * the Zustand stores (presence/typing), which is why nothing else needs to poll.
 */
export function useChatSocket() {
  const { token, isAuthenticated, user } = useAuth();
  const queryClient = useQueryClient();
  const setStatus = usePresenceStore((state) => state.setStatus);
  const setTyping = useChatStore((state) => state.setTyping);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      disconnectSocket();
      return;
    }

    connectSocket(token, {
      onMessage: (message: Message) => {
        queryClient.setQueryData<Message[]>(messagesQueryKey(message.conversationId), (old) => {
          if (!old) return [message];
          if (old.some((m) => m.id === message.id)) return old;
          return [...old, message];
        });
        queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
        queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      },
      onTyping: (event) => {
        setTyping(event.senderId, event.typing);
      },
      onPresence: (event) => {
        setStatus(event.userId, event.status);
      },
      onReadReceipt: (event) => {
        queryClient.setQueryData<Message[]>(messagesQueryKey(event.conversationId), (old) => {
          if (!old) return old;
          return old.map((m) =>
            m.senderId === user?.id && !m.readAt ? { ...m, readAt: event.readAt } : m,
          );
        });
      },
      onNotification: (notification: AppNotification) => {
        queryClient.setQueryData<AppNotification[]>(NOTIFICATIONS_QUERY_KEY, (old) =>
          old ? [notification, ...old] : [notification],
        );
        queryClient.setQueryData<number>(UNREAD_NOTIFICATIONS_QUERY_KEY, (old) => (old ?? 0) + 1);
        queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      },
    });

    return () => {
      disconnectSocket();
    };
  }, [isAuthenticated, token, user?.id, queryClient, setStatus, setTyping]);
}
