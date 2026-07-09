import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/ui/EmptyState";
import { useConversations } from "@/hooks/useConversations";
import {
  useMarkAllNotificationsAsRead,
  useMarkNotificationAsRead,
  useNotifications,
  useUnreadNotificationsCount,
} from "@/hooks/useNotifications";
import type { AppNotification } from "@/types/chat";

function timeAgo(isoDate: string): string {
  const seconds = Math.floor((Date.now() - new Date(isoDate).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  const { data: unreadCount } = useUnreadNotificationsCount();
  const { data: notifications } = useNotifications();
  const { data: conversations } = useConversations();
  const markAsRead = useMarkNotificationAsRead();
  const markAllAsRead = useMarkAllNotificationsAsRead();

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function handleNotificationClick(notification: AppNotification) {
    if (!notification.readAt) {
      markAsRead.mutate(notification.id);
    }
    const conversation = conversations?.find((c) => c.id === notification.relatedConversationId);
    if (conversation) {
      navigate(`/chat?with=${conversation.participant.id}`);
      setOpen(false);
    }
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative rounded-full p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
        aria-label="Notifications"
      >
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
          <path
            fillRule="evenodd"
            d="M5.25 9a6.75 6.75 0 0 1 13.5 0v.75c0 2.123.8 4.057 2.118 5.52a.75.75 0 0 1-.297 1.206c-1.544.57-3.16.99-4.831 1.243a3.75 3.75 0 1 1-7.48 0 24.585 24.585 0 0 1-4.83-1.244.75.75 0 0 1-.298-1.205A8.217 8.217 0 0 0 5.25 9.75V9Zm4.502 8.723a24.94 24.94 0 0 0 4.496 0 2.25 2.25 0 1 1-4.496 0Z"
            clipRule="evenodd"
          />
        </svg>
        {!!unreadCount && unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 rounded-lg border border-slate-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold text-slate-900">Notifications</p>
            {!!unreadCount && unreadCount > 0 && (
              <button
                onClick={() => markAllAsRead.mutate()}
                className="text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                Mark all as read
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {!notifications || notifications.length === 0 ? (
              <EmptyState message="You're all caught up." />
            ) : (
              <ul className="divide-y divide-slate-100">
                {notifications.map((notification) => (
                  <li key={notification.id}>
                    <button
                      onClick={() => handleNotificationClick(notification)}
                      className={`flex w-full flex-col items-start gap-0.5 px-4 py-3 text-left hover:bg-slate-50 ${
                        notification.readAt ? "" : "bg-brand-50/60"
                      }`}
                    >
                      <p className="text-sm font-medium text-slate-900">{notification.title}</p>
                      <p className="line-clamp-2 text-xs text-slate-500">{notification.body}</p>
                      <p className="text-[11px] text-slate-400">{timeAgo(notification.createdAt)}</p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
