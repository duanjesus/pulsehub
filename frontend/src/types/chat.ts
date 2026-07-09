import type { UserSummary } from "@/types/user";

export interface Message {
  id: number;
  conversationId: number;
  senderId: number;
  content: string;
  sentAt: string;
  readAt: string | null;
}

export interface Conversation {
  id: number;
  participant: UserSummary;
  lastMessage: Message | null;
  unreadCount: number;
}

export interface TypingEvent {
  conversationId: number;
  senderId: number;
  typing: boolean;
}

export interface ReadReceiptEvent {
  conversationId: number;
  readerId: number;
  readAt: string;
}

export type NotificationType = "NEW_MESSAGE";

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  relatedConversationId: number | null;
  readAt: string | null;
  createdAt: string;
}

export interface DashboardSummary {
  onlineUsersCount: number;
  onlineUsers: UserSummary[];
  recentConversations: Conversation[];
  unreadMessagesCount: number;
  recentNotifications: AppNotification[];
  unreadNotificationsCount: number;
}
