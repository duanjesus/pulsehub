import { useNavigate } from "react-router-dom";

import { Avatar } from "@/components/ui/Avatar";
import { EmptyState } from "@/components/ui/EmptyState";
import { Spinner } from "@/components/ui/Spinner";
import { useDashboard } from "@/hooks/useDashboard";
import { useConversations } from "@/hooks/useConversations";
import { useMarkNotificationAsRead } from "@/hooks/useNotifications";
import { usePresenceStore } from "@/store/presenceStore";
import type { AppNotification } from "@/types/chat";

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
  const { data: conversations } = useConversations();
  const statusByUserId = usePresenceStore((state) => state.statusByUserId);
  const markNotificationAsRead = useMarkNotificationAsRead();
  const navigate = useNavigate();

  if (isLoading || !data) {
    return (
      <div className="flex h-full items-center justify-center">
        <Spinner />
      </div>
    );
  }

  function openNotification(notification: AppNotification) {
    if (!notification.readAt) {
      markNotificationAsRead.mutate(notification.id);
    }
    const conversation = conversations?.find((c) => c.id === notification.relatedConversationId);
    if (conversation) {
      navigate(`/chat?with=${conversation.participant.id}`);
    }
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <h1 className="mb-6 text-xl font-semibold text-slate-900">Dashboard</h1>

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile label="Online users" value={data.onlineUsersCount} />
        <StatTile label="Unread messages" value={data.unreadMessagesCount} />
        <StatTile label="Unread notifications" value={data.unreadNotificationsCount} />
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
                    <Avatar name={u.name} avatarUrl={u.avatarUrl} status={statusByUserId[u.id] ?? u.status} size="sm" />
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
                      avatarUrl={c.participant.avatarUrl}
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

        <section className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
          <h2 className="mb-3 text-sm font-semibold text-slate-900">Notifications</h2>
          {data.recentNotifications.length === 0 ? (
            <EmptyState message="You're all caught up." />
          ) : (
            <ul className="space-y-3">
              {data.recentNotifications.map((notification) => (
                <li key={notification.id}>
                  <button
                    onClick={() => openNotification(notification)}
                    className={`flex w-full flex-col items-start gap-0.5 rounded-md px-2 py-1.5 text-left hover:bg-slate-50 ${
                      notification.readAt ? "" : "bg-brand-50/60"
                    }`}
                  >
                    <p className="truncate text-sm text-slate-800">{notification.title}</p>
                    <p className="line-clamp-1 text-xs text-slate-500">{notification.body}</p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
