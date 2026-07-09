import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { AppNotification } from "@/types/chat";
import type { Page } from "@/types/common";

export const NOTIFICATIONS_QUERY_KEY = ["notifications"];
export const UNREAD_NOTIFICATIONS_QUERY_KEY = ["notifications", "unread-count"];

export function useNotifications() {
  return useQuery({
    queryKey: NOTIFICATIONS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await api.get<Page<AppNotification>>("/notifications", { params: { size: 20 } });
      return data.content;
    },
  });
}

export function useUnreadNotificationsCount() {
  return useQuery({
    queryKey: UNREAD_NOTIFICATIONS_QUERY_KEY,
    queryFn: async () => {
      const { data } = await api.get<number>("/notifications/unread-count");
      return data;
    },
  });
}

export function useMarkNotificationAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (notificationId: number) => {
      await api.post(`/notifications/${notificationId}/read`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: UNREAD_NOTIFICATIONS_QUERY_KEY });
    },
  });
}

export function useMarkAllNotificationsAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await api.post("/notifications/read-all");
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: UNREAD_NOTIFICATIONS_QUERY_KEY });
    },
  });
}
