import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { Conversation, ParticipantSummary } from "@/types/chat";

export const CONVERSATIONS_QUERY_KEY = ["conversations"];

export function useConversations() {
  return useQuery({
    queryKey: CONVERSATIONS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await api.get<Conversation[]>("/conversations");
      return data;
    },
  });
}

export function useMarkConversationAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (conversationId: number) => {
      await api.post(`/conversations/${conversationId}/read`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useCreateDirectConversation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (otherUserId: number) => {
      const { data } = await api.post<Conversation>("/conversations/direct", { otherUserId });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useCreateGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: { name: string; memberIds: number[] }) => {
      const { data } = await api.post<Conversation>("/conversations/group", payload);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useConversationParticipants(conversationId: number | null) {
  return useQuery({
    queryKey: ["conversations", conversationId, "participants"],
    queryFn: async () => {
      const { data } = await api.get<ParticipantSummary[]>(`/conversations/${conversationId}/participants`);
      return data;
    },
    enabled: conversationId !== null,
  });
}

export function useAddMember(conversationId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (userId: number) => {
      await api.post(`/conversations/${conversationId}/members`, { userId });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["conversations", conversationId, "participants"] });
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useRemoveMember(conversationId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (userId: number) => {
      await api.delete(`/conversations/${conversationId}/members/${userId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["conversations", conversationId, "participants"] });
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}

export function useLeaveGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (conversationId: number) => {
      await api.post(`/conversations/${conversationId}/leave`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CONVERSATIONS_QUERY_KEY });
    },
  });
}
