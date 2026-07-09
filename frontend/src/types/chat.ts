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

export interface DashboardSummary {
  onlineUsersCount: number;
  onlineUsers: UserSummary[];
  recentConversations: Conversation[];
  unreadMessagesCount: number;
}
