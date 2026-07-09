import { useNavigate } from "react-router-dom";

import { Avatar } from "@/components/ui/Avatar";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useDashboard } from "@/hooks/useDashboard";
import { usePresenceStore } from "@/store/presenceStore";

function StatTile({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
    </div>
  );
}

export function DashboardPage() {
  const { data, isLoading } = useDashboard();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);
  const navigate = useNavigate();

  if (isLoading || !data) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <h1 className="mb-6 text-xl font-semibold text-slate-900">Dashboard</h1>

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile label="Online users" value={data.onlineUsersCount} />
        <StatTile label="Unread messages" value={data.unreadMessagesCount} />
        <StatTile label="Recent conversations" value={data.recentConversations.length} />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Online users</h2>
          {data.onlineUsers.length === 0 ? (
            <EmptyState message="No one else is online right now." />
          ) : (
            <ul className="space-y-3">
              {data.onlineUsers.map((u) => (
                <li key={u.id}>
                  <button
                    onClick={() => navigate(`/chat?with=${u.id}`)}
                    className="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left hover:bg-slate-50"
                  >
                    <Avatar name={u.name} status={statusByUserId[u.id] ?? u.status} size="sm" />
                    <span className="truncate text-sm text-slate-800">{u.name}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Recent conversations</h2>
          {data.recentConversations.length === 0 ? (
            <EmptyState message="Start a conversation from the Messages tab." />
          ) : (
            <ul className="space-y-3">
              {data.recentConversations.map((c) => (
                <li key={c.id}>
                  <button
                    onClick={() => navigate(`/chat?with=${c.participant.id}`)}
                    className="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left hover:bg-slate-50"
                  >
                    <Avatar
                      name={c.participant.name}
                      status={statusByUserId[c.participant.id] ?? c.participant.status}
                      size="sm"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm text-slate-800">{c.participant.name}</p>
                      <p className="truncate text-xs text-slate-500">
                        {c.lastMessage?.content ?? "No messages yet"}
                      </p>
                    </div>
                    {c.unreadCount > 0 && (
                      <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-600 px-1.5 text-xs font-semibold text-white">
                        {c.unreadCount}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="rounded-lg border border-dashed border-slate-300 bg-white p-5 lg:col-span-1">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Notifications</h2>
          <EmptyState message="Notifications are coming in V3." />
        </section>
      </div>
    </div>
  );
}
