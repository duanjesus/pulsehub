import { PresenceDot } from "@/components/ui/PresenceDot";
import type { UserStatus } from "@/types/user";

function initials(name: string): string {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function Avatar({
  name,
  status,
  size = "md",
}: {
  name: string;
  status?: UserStatus;
  size?: "sm" | "md";
}) {
  const dimension = size === "sm" ? "h-8 w-8 text-xs" : "h-10 w-10 text-sm";

  return (
    <span className="relative inline-flex shrink-0">
      <span
        className={`flex ${dimension} items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-700`}
      >
        {initials(name) || "?"}
      </span>
      {status && <PresenceDot status={status} className="absolute -bottom-0.5 -right-0.5" />}
    </span>
  );
}
