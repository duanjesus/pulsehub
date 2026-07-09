export type UserStatus = "ONLINE" | "AWAY" | "OFFLINE";

export const STATUS_LABELS: Record<UserStatus, string> = {
  ONLINE: "Online",
  AWAY: "Away",
  OFFLINE: "Offline",
};

export interface UserSummary {
  id: number;
  name: string;
  email: string;
  avatarUrl: string | null;
  status: UserStatus;
  lastSeenAt: string | null;
  createdAt: string;
}

export interface PresenceEvent {
  userId: number;
  status: UserStatus;
}
