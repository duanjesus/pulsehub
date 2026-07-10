import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

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

export function useSendVoiceMessage(conversationId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ blob, durationSeconds }: { blob: Blob; durationSeconds: number }) => {
      const formData = new FormData();
      formData.append("file", blob, "voice-message.webm");
      formData.append("durationSeconds", String(durationSeconds));

      const { data } = await api.post<Message>(`/conversations/${conversationId}/messages/voice`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      return data;
    },
    onSuccess: (message) => {
      queryClient.setQueryData<Message[]>(messagesQueryKey(conversationId), (old) => {
        if (!old) return [message];
        if (old.some((m) => m.id === message.id)) return old;
        return [...old, message];
      });
    },
  });
}
