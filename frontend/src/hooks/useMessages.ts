import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { Message } from "@/types/chat";
import type { Page } from "@/types/common";

export function messagesQueryKey(conversationId: number) {
  return ["conversations", conversationId, "messages"];
}

export function useMessages(conversationId: number | null) {
  return useQuery({
    queryKey: conversationId ? messagesQueryKey(conversationId) : ["conversations", "none", "messages"],
    queryFn: async () => {
      const { data } = await api.get<Page<Message>>(`/conversations/${conversationId}/messages`, {
        params: { size: 50 },
      });
      // Backend returns newest-first; the message list renders oldest-first.
      return [...data.content].reverse();
    },
    enabled: conversationId !== null,
  });
}
