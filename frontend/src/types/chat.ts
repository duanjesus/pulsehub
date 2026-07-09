import type { UserStatus, UserSummary } from "@/types/user";

export type ConversationType = "DIRECT" | "GROUP";
export type ParticipantRole = "OWNER" | "MEMBER";

export interface ParticipantSummary {
  userId: number;
  name: string;
  avatarUrl: string | null;
  status: UserStatus;
  role: ParticipantRole;
}

export interface Message {
  id: number;
  conversationId: number;
  senderId: number;
  content: string;
  sentAt: string;
  /** User ids (excluding the sender) who have read this message so far. */
  readBy: number[];
}

export interface Conversation {
  id: number;
  type: ConversationType;
  /** Group name, or (for DIRECT) the other participant's display name. */
  name: string;
  /** null for GROUP conversations. */
  avatarUrl: string | null;
  participants: ParticipantSummary[];
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
