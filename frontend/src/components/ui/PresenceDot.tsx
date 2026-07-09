import type { UserStatus } from "@/types/user";

const STATUS_CLASSES: Record<UserStatus, string> = {
  ONLINE: "bg-presence-online",
  AWAY: "bg-presence-away",
  OFFLINE: "bg-presence-offline",
};

export function PresenceDot({ status, className = "" }: { status: UserStatus; className?: string }) {
  return (
    <span
      className={`inline-block h-2.5 w-2.5 rounded-full ring-2 ring-white ${STATUS_CLASSES[status]} ${className}`}
      aria-label={status}
    />
  );
}
